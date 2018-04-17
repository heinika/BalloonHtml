package com.github.heinika;

/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.app.ActivityManager;
import android.app.Application;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.BulletSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.ParagraphStyle;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class processes HTML strings into displayable styled text.
 * Not all HTML tags are supported.
 */
public class Html {
    /**
     * Retrieves images for HTML &lt;img&gt; tags.
     */
    public static interface ImageGetter {
        /**
         * This method is called when the HTML parser encounters an
         * &lt;img&gt; tag.  The <code>source</code> argument is the
         * string from the "src" attribute; the return value should be
         * a Drawable representation of the image or <code>null</code>
         * for a generic replacement image.  Make sure you call
         * setBounds() on your Drawable if it doesn't already have
         * its bounds set.
         */
        public Drawable getDrawable(String source, int width, int height);
    }

    /**
     * Is notified when HTML tags are encountered that the parser does
     * not know how to interpret.
     */
    public static interface TagHandler {
        /**
         * This method will be called whenn the HTML parser encounters
         * a tag that it does not know how to interpret.
         */
        public void handleTag(boolean opening, String tag,
                              Editable output, XMLReader xmlReader);
    }

    /**
     * Option for {@link #toHtml(Spanned, int)}: Wrap consecutive lines of text delimited by '\n'
     * inside &lt;p&gt; elements. {@link BulletSpan}s are ignored.
     */
    public static final int TO_HTML_PARAGRAPH_LINES_CONSECUTIVE = 0x00000000;

    /**
     * Option for {@link #toHtml(Spanned, int)}: Wrap each line of text delimited by '\n' inside a
     * &lt;p&gt; or a &lt;li&gt; element. This allows {@link ParagraphStyle}s attached to be
     * encoded as CSS styles within the corresponding &lt;p&gt; or &lt;li&gt; element.
     */
    public static final int TO_HTML_PARAGRAPH_LINES_INDIVIDUAL = 0x00000001;

    /**
     * Flag indicating that texts inside &lt;p&gt; elements will be separated from other texts with
     * one newline character by default.
     */
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH = 0x00000001;

    /**
     * Flag indicating that texts inside &lt;h1&gt;~&lt;h6&gt; elements will be separated from
     * other texts with one newline character by default.
     */
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_HEADING = 0x00000002;

    /**
     * Flag indicating that texts inside &lt;li&gt; elements will be separated from other texts
     * with one newline character by default.
     */
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM = 0x00000004;

    /**
     * Flag indicating that texts inside &lt;ul&gt; elements will be separated from other texts
     * with one newline character by default.
     */
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_LIST = 0x00000008;

    /**
     * Flag indicating that texts inside &lt;div&gt; elements will be separated from other texts
     * with one newline character by default.
     */
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_DIV = 0x00000010;

    /**
     * Flag indicating that texts inside &lt;blockquote&gt; elements will be separated from other
     * texts with one newline character by default.
     */
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_BLOCKQUOTE = 0x00000020;

    /**
     * Flag indicating that CSS color values should be used instead of those defined in
     * {@link Color}.
     */
    public static final int FROM_HTML_OPTION_USE_CSS_COLORS = 0x00000100;

    /**
     * Flags for {@link (String, int, android.text.Html.ImageGetter, android.text.Html.TagHandler)}: Separate block-level
     * elements with blank lines (two newline characters) in between. This is the legacy behavior
     * prior to N.
     */
    public static final int FROM_HTML_MODE_LEGACY = 0x00000000;

    /**
     * Flags for {@link (String, int, android.text.Html.ImageGetter, android.text.Html.TagHandler)}: Separate block-level
     * elements with line breaks (single newline character) in between. This inverts the
     * {@link Spanned} to HTML string conversion done with the option
     * {@link #TO_HTML_PARAGRAPH_LINES_INDIVIDUAL}.
     */
    public static final int FROM_HTML_MODE_COMPACT =
            FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH
                    | FROM_HTML_SEPARATOR_LINE_BREAK_HEADING
                    | FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM
                    | FROM_HTML_SEPARATOR_LINE_BREAK_LIST
                    | FROM_HTML_SEPARATOR_LINE_BREAK_DIV
                    | FROM_HTML_SEPARATOR_LINE_BREAK_BLOCKQUOTE;

    /**
     * The bit which indicates if lines delimited by '\n' will be grouped into &lt;p&gt; elements.
     */
    private static final int TO_HTML_PARAGRAPH_FLAG = 0x00000001;

    private Html() {
    }

    /**
     * Returns displayable styled text from the provided HTML string with the legacy flags
     * {@link #FROM_HTML_MODE_LEGACY}.
     *
     * @deprecated use {@link #fromHtml(String, int)} instead.
     */
    @Deprecated
    public static Spanned fromHtml(String source) {
        return fromHtml(source, FROM_HTML_MODE_LEGACY, null, null);
    }

    /**
     * Returns displayable styled text from the provided HTML string. Any &lt;img&gt; tags in the
     * HTML will display as a generic replacement image which your program can then go through and
     * replace with real images.
     * <p>
     * <p>This uses TagSoup to handle real HTML, including all of the brokenness found in the wild.
     */
    public static Spanned fromHtml(String source, int flags) {
        return fromHtml(source, flags, null, null);
    }

    /**
     * Lazy initialization holder for HTML parser. This class will
     * a) be preloaded by the zygote, or b) not loaded until absolutely
     * necessary.
     */
    private static class HtmlParser {

        private static Object schema;

        static {
            try {
                Class htmlSchemaClass = Class.forName("org.ccil.cowan.tagsoup.HTMLSchema");
                schema = htmlSchemaClass.newInstance();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Returns displayable styled text from the provided HTML string with the legacy flags
     * {@link #FROM_HTML_MODE_LEGACY}.
     * <p>
     * use {@link (String, int, android.text.Html.ImageGetter, android.text.Html.TagHandler)} instead.
     */
    public static Spanned fromHtml(String source, Html.ImageGetter imageGetter, android.text.Html.TagHandler tagHandler) {
        return fromHtml(source, FROM_HTML_MODE_LEGACY, imageGetter, tagHandler);
    }

    /**
     * Returns displayable styled text from the provided HTML string. Any &lt;img&gt; tags in the
     * HTML will use the specified ImageGetter to request a representation of the image (use null
     * if you don't want this) and the specified TagHandler to handle unknown tags (specify null if
     * you don't want this).
     * <p>
     * <p>This uses TagSoup to handle real HTML, including all of the brokenness found in the wild.
     */
    public static Spanned fromHtml(String source, int flags, Html.ImageGetter imageGetter,
                                   android.text.Html.TagHandler tagHandler) {
        String schemaProperty = "http://www.ccil.org/~cowan/tagsoup/properties/schema";
        XMLReader parser = null;
        try {
            Class parserClass = Class.forName("org.ccil.cowan.tagsoup.Parser");
            parser = (XMLReader) parserClass.newInstance();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        try {
            parser.setProperty(schemaProperty, HtmlParser.schema);
        } catch (org.xml.sax.SAXNotRecognizedException e) {
            // Should not happen.
            throw new RuntimeException(e);
        } catch (org.xml.sax.SAXNotSupportedException e) {
            // Should not happen.
            throw new RuntimeException(e);
        }

        HtmlToSpannedConverter converter =
                new HtmlToSpannedConverter(source, imageGetter, tagHandler, parser, flags);
        return converter.convert();
    }

    /**
     * @deprecated use {@link #toHtml(Spanned, int)} instead.
     */
    @Deprecated
    public static String toHtml(Spanned text) {
        return toHtml(text, TO_HTML_PARAGRAPH_LINES_CONSECUTIVE);
    }

    /**
     * Returns an HTML representation of the provided Spanned text. A best effort is
     * made to add HTML tags corresponding to spans. Also note that HTML metacharacters
     * (such as "&lt;" and "&amp;") within the input text are escaped.
     *
     * @param text   input text to convert
     * @param option one of {@link #TO_HTML_PARAGRAPH_LINES_CONSECUTIVE} or
     *               {@link #TO_HTML_PARAGRAPH_LINES_INDIVIDUAL}
     * @return string containing input converted to HTML
     */
    public static String toHtml(Spanned text, int option) {
        StringBuilder out = new StringBuilder();
        withinHtml(out, text, option);
        return out.toString();
    }

    /**
     * Returns an HTML escaped representation of the given plain text.
     */
    public static String escapeHtml(CharSequence text) {
        StringBuilder out = new StringBuilder();
        withinStyle(out, text, 0, text.length());
        return out.toString();
    }

    private static void withinHtml(StringBuilder out, Spanned text, int option) {
        if ((option & TO_HTML_PARAGRAPH_FLAG) == TO_HTML_PARAGRAPH_LINES_CONSECUTIVE) {
            encodeTextAlignmentByDiv(out, text, option);
            return;
        }

        withinDiv(out, text, 0, text.length(), option);
    }

    private static void encodeTextAlignmentByDiv(StringBuilder out, Spanned text, int option) {
        int len = text.length();

        int next;
        for (int i = 0; i < len; i = next) {
            next = text.nextSpanTransition(i, len, ParagraphStyle.class);
            ParagraphStyle[] style = text.getSpans(i, next, ParagraphStyle.class);
            String elements = " ";
            boolean needDiv = false;

            for (int j = 0; j < style.length; j++) {
                if (style[j] instanceof AlignmentSpan) {
                    Layout.Alignment align =
                            ((AlignmentSpan) style[j]).getAlignment();
                    needDiv = true;
                    if (align == Layout.Alignment.ALIGN_CENTER) {
                        elements = "align=\"center\" " + elements;
                    } else if (align == Layout.Alignment.ALIGN_OPPOSITE) {
                        elements = "align=\"right\" " + elements;
                    } else {
                        elements = "align=\"left\" " + elements;
                    }
                }
            }
            if (needDiv) {
                out.append("<div ").append(elements).append(">");
            }

            withinDiv(out, text, i, next, option);

            if (needDiv) {
                out.append("</div>");
            }
        }
    }

    private static void withinDiv(StringBuilder out, Spanned text, int start, int end,
                                  int option) {
        int next;
        for (int i = start; i < end; i = next) {
            next = text.nextSpanTransition(i, end, QuoteSpan.class);
            QuoteSpan[] quotes = text.getSpans(i, next, QuoteSpan.class);

            for (QuoteSpan quote : quotes) {
                out.append("<blockquote>");
            }

            withinBlockquote(out, text, i, next, option);

            for (QuoteSpan quote : quotes) {
                out.append("</blockquote>\n");
            }
        }
    }

    private static String getTextDirection(Spanned text, int start, int end) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            if (TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(text, start, end - start)) {
                return " dir=\"rtl\"";
            } else {
                return " dir=\"ltr\"";
            }
        }
        return " ";
    }

    private static String getTextStyles(Spanned text, int start, int end,
                                        boolean forceNoVerticalMargin, boolean includeTextAlign) {
        String margin = null;
        String textAlign = null;

        if (forceNoVerticalMargin) {
            margin = "margin-top:0; margin-bottom:0;";
        }
        if (includeTextAlign) {
            final AlignmentSpan[] alignmentSpans = text.getSpans(start, end, AlignmentSpan.class);

            // Only use the last AlignmentSpan with flag SPAN_PARAGRAPH
            for (int i = alignmentSpans.length - 1; i >= 0; i--) {
                AlignmentSpan s = alignmentSpans[i];
                if ((text.getSpanFlags(s) & Spanned.SPAN_PARAGRAPH) == Spanned.SPAN_PARAGRAPH) {
                    final Layout.Alignment alignment = s.getAlignment();
                    if (alignment == Layout.Alignment.ALIGN_NORMAL) {
                        textAlign = "text-align:start;";
                    } else if (alignment == Layout.Alignment.ALIGN_CENTER) {
                        textAlign = "text-align:center;";
                    } else if (alignment == Layout.Alignment.ALIGN_OPPOSITE) {
                        textAlign = "text-align:end;";
                    }
                    break;
                }
            }
        }

        if (margin == null && textAlign == null) {
            return "";
        }

        final StringBuilder style = new StringBuilder(" style=\"");
        if (margin != null && textAlign != null) {
            style.append(margin).append(" ").append(textAlign);
        } else if (margin != null) {
            style.append(margin);
        } else if (textAlign != null) {
            style.append(textAlign);
        }

        return style.append("\"").toString();
    }

    private static void withinBlockquote(StringBuilder out, Spanned text, int start, int end,
                                         int option) {
        if ((option & TO_HTML_PARAGRAPH_FLAG) == TO_HTML_PARAGRAPH_LINES_CONSECUTIVE) {
            withinBlockquoteConsecutive(out, text, start, end);
        } else {
            withinBlockquoteIndividual(out, text, start, end);
        }
    }

    private static void withinBlockquoteIndividual(StringBuilder out, Spanned text, int start,
                                                   int end) {
        boolean isInList = false;
        int next;
        for (int i = start; i <= end; i = next) {
            next = TextUtils.indexOf(text, '\n', i, end);
            if (next < 0) {
                next = end;
            }

            if (next == i) {
                if (isInList) {
                    // Current paragraph is no longer a list item; close the previously opened list
                    isInList = false;
                    out.append("</ul>\n");
                }
                out.append("<br>\n");
            } else {
                boolean isListItem = false;
                ParagraphStyle[] paragraphStyles = text.getSpans(i, next, ParagraphStyle.class);
                for (ParagraphStyle paragraphStyle : paragraphStyles) {
                    final int spanFlags = text.getSpanFlags(paragraphStyle);
                    if ((spanFlags & Spanned.SPAN_PARAGRAPH) == Spanned.SPAN_PARAGRAPH
                            && paragraphStyle instanceof BulletSpan) {
                        isListItem = true;
                        break;
                    }
                }

                if (isListItem && !isInList) {
                    // Current paragraph is the first item in a list
                    isInList = true;
                    out.append("<ul")
                            .append(getTextStyles(text, i, next, true, false))
                            .append(">\n");
                }

                if (isInList && !isListItem) {
                    // Current paragraph is no longer a list item; close the previously opened list
                    isInList = false;
                    out.append("</ul>\n");
                }

                String tagType = isListItem ? "li" : "p";
                out.append("<").append(tagType)
                        .append(getTextDirection(text, i, next))
                        .append(getTextStyles(text, i, next, !isListItem, true))
                        .append(">");

                withinParagraph(out, text, i, next);

                out.append("</");
                out.append(tagType);
                out.append(">\n");

                if (next == end && isInList) {
                    isInList = false;
                    out.append("</ul>\n");
                }
            }

            next++;
        }
    }

    private static void withinBlockquoteConsecutive(StringBuilder out, Spanned text, int start,
                                                    int end) {
        out.append("<p").append(getTextDirection(text, start, end)).append(">");

        int next;
        for (int i = start; i < end; i = next) {
            next = TextUtils.indexOf(text, '\n', i, end);
            if (next < 0) {
                next = end;
            }

            int nl = 0;

            while (next < end && text.charAt(next) == '\n') {
                nl++;
                next++;
            }

            withinParagraph(out, text, i, next - nl);

            if (nl == 1) {
                out.append("<br>\n");
            } else {
                for (int j = 2; j < nl; j++) {
                    out.append("<br>");
                }
                if (next != end) {
                    /* Paragraph should be closed and reopened */
                    out.append("</p>\n");
                    out.append("<p").append(getTextDirection(text, start, end)).append(">");
                }
            }
        }

        out.append("</p>\n");
    }

    private static void withinParagraph(StringBuilder out, Spanned text, int start, int end) {
        int next;
        for (int i = start; i < end; i = next) {
            next = text.nextSpanTransition(i, end, CharacterStyle.class);
            CharacterStyle[] style = text.getSpans(i, next, CharacterStyle.class);

            for (int j = 0; j < style.length; j++) {
                if (style[j] instanceof StyleSpan) {
                    int s = ((StyleSpan) style[j]).getStyle();

                    if ((s & Typeface.BOLD) != 0) {
                        out.append("<b>");
                    }
                    if ((s & Typeface.ITALIC) != 0) {
                        out.append("<i>");
                    }
                }
                if (style[j] instanceof TypefaceSpan) {
                    String s = ((TypefaceSpan) style[j]).getFamily();

                    if ("monospace".equals(s)) {
                        out.append("<tt>");
                    }
                }
                if (style[j] instanceof SuperscriptSpan) {
                    out.append("<sup>");
                }
                if (style[j] instanceof SubscriptSpan) {
                    out.append("<sub>");
                }
                if (style[j] instanceof UnderlineSpan) {
                    out.append("<u>");
                }
                if (style[j] instanceof StrikethroughSpan) {
                    out.append("<span style=\"text-decoration:line-through;\">");
                }
                if (style[j] instanceof URLSpan) {
                    out.append("<a href=\"");
                    out.append(((URLSpan) style[j]).getURL());
                    out.append("\">");
                }
                if (style[j] instanceof ImageSpan) {
                    out.append("<img src=\"");
                    out.append(((ImageSpan) style[j]).getSource());
                    out.append("\">");

                    // Don't output the dummy character underlying the image.
                    i = next;
                }
                if (style[j] instanceof AbsoluteSizeSpan) {
                    AbsoluteSizeSpan s = ((AbsoluteSizeSpan) style[j]);
                    float sizeDip = s.getSize();

                    // px in CSS is the equivalance of dip in Android
                    out.append(String.format("<span style=\"font-size:%.0fpx\";>", sizeDip));
                }
                if (style[j] instanceof RelativeSizeSpan) {
                    float sizeEm = ((RelativeSizeSpan) style[j]).getSizeChange();
                    out.append(String.format("<span style=\"font-size:%.2fem;\">", sizeEm));
                }
                if (style[j] instanceof ForegroundColorSpan) {
                    int color = ((ForegroundColorSpan) style[j]).getForegroundColor();
                    out.append(String.format("<span style=\"color:#%06X;\">", 0xFFFFFF & color));
                }
                if (style[j] instanceof BackgroundColorSpan) {
                    int color = ((BackgroundColorSpan) style[j]).getBackgroundColor();
                    out.append(String.format("<span style=\"background-color:#%06X;\">",
                            0xFFFFFF & color));
                }
            }

            withinStyle(out, text, i, next);

            for (int j = style.length - 1; j >= 0; j--) {
                if (style[j] instanceof BackgroundColorSpan) {
                    out.append("</span>");
                }
                if (style[j] instanceof ForegroundColorSpan) {
                    out.append("</span>");
                }
                if (style[j] instanceof RelativeSizeSpan) {
                    out.append("</span>");
                }
                if (style[j] instanceof AbsoluteSizeSpan) {
                    out.append("</span>");
                }
                if (style[j] instanceof URLSpan) {
                    out.append("</a>");
                }
                if (style[j] instanceof StrikethroughSpan) {
                    out.append("</span>");
                }
                if (style[j] instanceof UnderlineSpan) {
                    out.append("</u>");
                }
                if (style[j] instanceof SubscriptSpan) {
                    out.append("</sub>");
                }
                if (style[j] instanceof SuperscriptSpan) {
                    out.append("</sup>");
                }
                if (style[j] instanceof TypefaceSpan) {
                    String s = ((TypefaceSpan) style[j]).getFamily();

                    if (s.equals("monospace")) {
                        out.append("</tt>");
                    }
                }
                if (style[j] instanceof StyleSpan) {
                    int s = ((StyleSpan) style[j]).getStyle();

                    if ((s & Typeface.BOLD) != 0) {
                        out.append("</b>");
                    }
                    if ((s & Typeface.ITALIC) != 0) {
                        out.append("</i>");
                    }
                }
            }
        }
    }

    private static void withinStyle(StringBuilder out, CharSequence text,
                                    int start, int end) {
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);

            if (c == '<') {
                out.append("&lt;");
            } else if (c == '>') {
                out.append("&gt;");
            } else if (c == '&') {
                out.append("&amp;");
            } else if (c >= 0xD800 && c <= 0xDFFF) {
                if (c < 0xDC00 && i + 1 < end) {
                    char d = text.charAt(i + 1);
                    if (d >= 0xDC00 && d <= 0xDFFF) {
                        i++;
                        int codepoint = 0x010000 | (int) c - 0xD800 << 10 | (int) d - 0xDC00;
                        out.append("&#").append(codepoint).append(";");
                    }
                }
            } else if (c > 0x7E || c < ' ') {
                out.append("&#").append((int) c).append(";");
            } else if (c == ' ') {
                while (i + 1 < end && text.charAt(i + 1) == ' ') {
                    out.append("&nbsp;");
                    i++;
                }

                out.append(' ');
            } else {
                out.append(c);
            }
        }
    }
}

class HtmlToSpannedConverter implements ContentHandler {

    private static final float[] HEADING_SIZES = {
            1.5f, 1.4f, 1.3f, 1.2f, 1.1f, 1f,
    };

    private String mSource;
    private XMLReader mReader;
    private SpannableStringBuilder mSpannableStringBuilder;
    private Html.ImageGetter mImageGetter;
    private android.text.Html.TagHandler mTagHandler;
    private int mFlags;

    private static Pattern sTextAlignPattern;
    private static Pattern sForegroundColorPattern;
    private static Pattern sBackgroundColorPattern;
    private static Pattern sTextDecorationPattern;

    /**
     * Name-value mapping of HTML/CSS colors which have different values in {@link Color}.
     */
    private static final Map<String, Integer> sColorMap;

    static {
        sColorMap = new HashMap<>();
        sColorMap.put("darkgray", 0xFFA9A9A9);
        sColorMap.put("gray", 0xFF808080);
        sColorMap.put("lightgray", 0xFFD3D3D3);
        sColorMap.put("darkgrey", 0xFFA9A9A9);
        sColorMap.put("grey", 0xFF808080);
        sColorMap.put("lightgrey", 0xFFD3D3D3);
        sColorMap.put("green", 0xFF008000);
    }

    private static Pattern getTextAlignPattern() {
        if (sTextAlignPattern == null) {
            sTextAlignPattern = Pattern.compile("(?:\\s+|\\A)text-align\\s*:\\s*(\\S*)\\b");
        }
        return sTextAlignPattern;
    }

    private static Pattern getForegroundColorPattern() {
        if (sForegroundColorPattern == null) {
            sForegroundColorPattern = Pattern.compile(
                    "(?:\\s+|\\A)color\\s*:\\s*(\\S*)\\b");
        }
        return sForegroundColorPattern;
    }

    private static Pattern getBackgroundColorPattern() {
        if (sBackgroundColorPattern == null) {
            sBackgroundColorPattern = Pattern.compile(
                    "(?:\\s+|\\A)background(?:-color)?\\s*:\\s*(\\S*)\\b");
        }
        return sBackgroundColorPattern;
    }

    private static Pattern getTextDecorationPattern() {
        if (sTextDecorationPattern == null) {
            sTextDecorationPattern = Pattern.compile(
                    "(?:\\s+|\\A)text-decoration\\s*:\\s*(\\S*)\\b");
        }
        return sTextDecorationPattern;
    }

    public HtmlToSpannedConverter(String source, Html.ImageGetter imageGetter,
                                  android.text.Html.TagHandler tagHandler, XMLReader parser, int flags) {
        mSource = source;
        mSpannableStringBuilder = new SpannableStringBuilder();
        mImageGetter = imageGetter;
        mTagHandler = tagHandler;
        mReader = parser;
        mFlags = flags;
    }

    public Spanned convert() {

        mReader.setContentHandler(this);
        try {
            mReader.parse(new InputSource(new StringReader(mSource)));
        } catch (IOException e) {
            // We are reading from a string. There should not be IO problems.
            throw new RuntimeException(e);
        } catch (SAXException e) {
            // TagSoup doesn't throw parse exceptions.
            throw new RuntimeException(e);
        }

        // Fix flags and range for paragraph-type markup.
        Object[] obj = mSpannableStringBuilder.getSpans(0, mSpannableStringBuilder.length(), ParagraphStyle.class);
        for (int i = 0; i < obj.length; i++) {
            int start = mSpannableStringBuilder.getSpanStart(obj[i]);
            int end = mSpannableStringBuilder.getSpanEnd(obj[i]);

            // If the last line of the range is blank, back off by one.
            if (end - 2 >= 0) {
                if (mSpannableStringBuilder.charAt(end - 1) == '\n' &&
                        mSpannableStringBuilder.charAt(end - 2) == '\n') {
                    end--;
                }
            }

            if (end == start) {
                mSpannableStringBuilder.removeSpan(obj[i]);
            } else {
                mSpannableStringBuilder.setSpan(obj[i], start, end, Spannable.SPAN_PARAGRAPH);
            }
        }

        return mSpannableStringBuilder;
    }

    private void handleStartTag(String tag, Attributes attributes) {
        if (tag.equalsIgnoreCase("br")) {
            // We don't need to handle this. TagSoup will ensure that there's a </br> for each <br>
            // so we can safely emit the linebreaks when we handle the close tag.
        } else if (tag.equalsIgnoreCase("p")) {
            startBlockElement(mSpannableStringBuilder, attributes, getMarginParagraph());
            startCssStyle(mSpannableStringBuilder, attributes);
        } else if (tag.equalsIgnoreCase("ul")) {
            startBlockElement(mSpannableStringBuilder, attributes, getMarginList());
        } else if (tag.equalsIgnoreCase("li")) {
            startLi(mSpannableStringBuilder, attributes);
        } else if (tag.equalsIgnoreCase("div")) {
            startBlockElement(mSpannableStringBuilder, attributes, getMarginDiv());
        } else if (tag.equalsIgnoreCase("span")) {
            startCssStyle(mSpannableStringBuilder, attributes);
        } else if (tag.equalsIgnoreCase("strong")) {
            start(mSpannableStringBuilder, new Bold());
        } else if (tag.equalsIgnoreCase("b")) {
            start(mSpannableStringBuilder, new Bold());
        } else if (tag.equalsIgnoreCase("em")) {
            start(mSpannableStringBuilder, new Italic());
        } else if (tag.equalsIgnoreCase("cite")) {
            start(mSpannableStringBuilder, new Italic());
        } else if (tag.equalsIgnoreCase("dfn")) {
            start(mSpannableStringBuilder, new Italic());
        } else if (tag.equalsIgnoreCase("i")) {
            start(mSpannableStringBuilder, new Italic());
        } else if (tag.equalsIgnoreCase("big")) {
            start(mSpannableStringBuilder, new Big());
        } else if (tag.equalsIgnoreCase("small")) {
            start(mSpannableStringBuilder, new Small());
        } else if (tag.equalsIgnoreCase("font")) {
            startFont(mSpannableStringBuilder, attributes);
        } else if (tag.equalsIgnoreCase("blockquote")) {
            startBlockquote(mSpannableStringBuilder, attributes);
        } else if (tag.equalsIgnoreCase("tt")) {
            start(mSpannableStringBuilder, new Monospace());
        } else if (tag.equalsIgnoreCase("a")) {
            startA(mSpannableStringBuilder, attributes);
        } else if (tag.equalsIgnoreCase("u")) {
            start(mSpannableStringBuilder, new Underline());
        } else if (tag.equalsIgnoreCase("del")) {
            start(mSpannableStringBuilder, new Strikethrough());
        } else if (tag.equalsIgnoreCase("s")) {
            start(mSpannableStringBuilder, new Strikethrough());
        } else if (tag.equalsIgnoreCase("strike")) {
            start(mSpannableStringBuilder, new Strikethrough());
        } else if (tag.equalsIgnoreCase("sup")) {
            start(mSpannableStringBuilder, new Super());
        } else if (tag.equalsIgnoreCase("sub")) {
            start(mSpannableStringBuilder, new Sub());
        } else if (tag.length() == 2 &&
                Character.toLowerCase(tag.charAt(0)) == 'h' &&
                tag.charAt(1) >= '1' && tag.charAt(1) <= '6') {
            startHeading(mSpannableStringBuilder, attributes, tag.charAt(1) - '1');
        } else if (tag.equalsIgnoreCase("img")) {
            startImg(mSpannableStringBuilder, attributes, mImageGetter);
        } else if (mTagHandler != null) {
            mTagHandler.handleTag(true, tag, mSpannableStringBuilder, mReader);
        }
    }

    private void handleEndTag(String tag) {
        if (tag.equalsIgnoreCase("br")) {
            handleBr(mSpannableStringBuilder);
        } else if (tag.equalsIgnoreCase("p")) {
            endCssStyle(mSpannableStringBuilder);
            endBlockElement(mSpannableStringBuilder);
        } else if (tag.equalsIgnoreCase("ul")) {
            endBlockElement(mSpannableStringBuilder);
        } else if (tag.equalsIgnoreCase("li")) {
            endLi(mSpannableStringBuilder);
        } else if (tag.equalsIgnoreCase("div")) {
            endBlockElement(mSpannableStringBuilder);
        } else if (tag.equalsIgnoreCase("span")) {
            endCssStyle(mSpannableStringBuilder);
        } else if (tag.equalsIgnoreCase("strong")) {
            end(mSpannableStringBuilder, Bold.class, new StyleSpan(Typeface.BOLD));
        } else if (tag.equalsIgnoreCase("b")) {
            end(mSpannableStringBuilder, Bold.class, new StyleSpan(Typeface.BOLD));
        } else if (tag.equalsIgnoreCase("em")) {
            end(mSpannableStringBuilder, Italic.class, new StyleSpan(Typeface.ITALIC));
        } else if (tag.equalsIgnoreCase("cite")) {
            end(mSpannableStringBuilder, Italic.class, new StyleSpan(Typeface.ITALIC));
        } else if (tag.equalsIgnoreCase("dfn")) {
            end(mSpannableStringBuilder, Italic.class, new StyleSpan(Typeface.ITALIC));
        } else if (tag.equalsIgnoreCase("i")) {
            end(mSpannableStringBuilder, Italic.class, new StyleSpan(Typeface.ITALIC));
        } else if (tag.equalsIgnoreCase("big")) {
            end(mSpannableStringBuilder, Big.class, new RelativeSizeSpan(1.25f));
        } else if (tag.equalsIgnoreCase("small")) {
            end(mSpannableStringBuilder, Small.class, new RelativeSizeSpan(0.8f));
        } else if (tag.equalsIgnoreCase("font")) {
            endFont(mSpannableStringBuilder);
        } else if (tag.equalsIgnoreCase("blockquote")) {
            endBlockquote(mSpannableStringBuilder);
        } else if (tag.equalsIgnoreCase("tt")) {
            end(mSpannableStringBuilder, Monospace.class, new TypefaceSpan("monospace"));
        } else if (tag.equalsIgnoreCase("a")) {
            endA(mSpannableStringBuilder);
        } else if (tag.equalsIgnoreCase("u")) {
            end(mSpannableStringBuilder, Underline.class, new UnderlineSpan());
        } else if (tag.equalsIgnoreCase("del")) {
            end(mSpannableStringBuilder, Strikethrough.class, new StrikethroughSpan());
        } else if (tag.equalsIgnoreCase("s")) {
            end(mSpannableStringBuilder, Strikethrough.class, new StrikethroughSpan());
        } else if (tag.equalsIgnoreCase("strike")) {
            end(mSpannableStringBuilder, Strikethrough.class, new StrikethroughSpan());
        } else if (tag.equalsIgnoreCase("sup")) {
            end(mSpannableStringBuilder, Super.class, new SuperscriptSpan());
        } else if (tag.equalsIgnoreCase("sub")) {
            end(mSpannableStringBuilder, Sub.class, new SubscriptSpan());
        } else if (tag.length() == 2 &&
                Character.toLowerCase(tag.charAt(0)) == 'h' &&
                tag.charAt(1) >= '1' && tag.charAt(1) <= '6') {
            endHeading(mSpannableStringBuilder);
        } else if (mTagHandler != null) {
            mTagHandler.handleTag(false, tag, mSpannableStringBuilder, mReader);
        }
    }

    private int getMarginParagraph() {
        return getMargin(android.text.Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH);
    }

    private int getMarginHeading() {
        return getMargin(android.text.Html.FROM_HTML_SEPARATOR_LINE_BREAK_HEADING);
    }

    private int getMarginListItem() {
        return getMargin(android.text.Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM);
    }

    private int getMarginList() {
        return getMargin(android.text.Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST);
    }

    private int getMarginDiv() {
        return getMargin(android.text.Html.FROM_HTML_SEPARATOR_LINE_BREAK_DIV);
    }

    private int getMarginBlockquote() {
        return getMargin(android.text.Html.FROM_HTML_SEPARATOR_LINE_BREAK_BLOCKQUOTE);
    }

    /**
     * Returns the minimum number of newline characters needed before and after a given block-level
     * element.
     *
     * @param flag the corresponding option flag defined in {@link android.text.Html} of a block-level element
     */
    private int getMargin(int flag) {
        if ((flag & mFlags) != 0) {
            return 1;
        }
        return 2;
    }

    private static void appendNewlines(Editable text, int minNewline) {
        final int len = text.length();

        if (len == 0) {
            return;
        }

        int existingNewlines = 0;
        for (int i = len - 1; i >= 0 && text.charAt(i) == '\n'; i--) {
            existingNewlines++;
        }

        for (int j = existingNewlines; j < minNewline; j++) {
            text.append("\n");
        }
    }

    private static void startBlockElement(Editable text, Attributes attributes, int margin) {
        final int len = text.length();
        if (margin > 0) {
            appendNewlines(text, margin);
            start(text, new Newline(margin));
        }

        String style = attributes.getValue("", "style");
        if (style != null) {
            Matcher m = getTextAlignPattern().matcher(style);
            if (m.find()) {
                String alignment = m.group(1);
                if (alignment.equalsIgnoreCase("start")) {
                    start(text, new Alignment(Layout.Alignment.ALIGN_NORMAL));
                } else if (alignment.equalsIgnoreCase("center")) {
                    start(text, new Alignment(Layout.Alignment.ALIGN_CENTER));
                } else if (alignment.equalsIgnoreCase("end")) {
                    start(text, new Alignment(Layout.Alignment.ALIGN_OPPOSITE));
                }
            }
        }
    }

    private static void endBlockElement(Editable text) {
        Newline n = getLast(text, Newline.class);
        if (n != null) {
            appendNewlines(text, n.mNumNewlines);
            text.removeSpan(n);
        }

        Alignment a = getLast(text, Alignment.class);
        if (a != null) {
            setSpanFromMark(text, a, new AlignmentSpan.Standard(a.mAlignment));
        }
    }

    private static void handleBr(Editable text) {
        text.append('\n');
    }

    private void startLi(Editable text, Attributes attributes) {
        startBlockElement(text, attributes, getMarginListItem());
        start(text, new Bullet());
        startCssStyle(text, attributes);
    }

    private static void endLi(Editable text) {
        endCssStyle(text);
        endBlockElement(text);
        end(text, Bullet.class, new BulletSpan());
    }

    private void startBlockquote(Editable text, Attributes attributes) {
        startBlockElement(text, attributes, getMarginBlockquote());
        start(text, new Blockquote());
    }

    private static void endBlockquote(Editable text) {
        endBlockElement(text);
        end(text, Blockquote.class, new QuoteSpan());
    }

    private void startHeading(Editable text, Attributes attributes, int level) {
        startBlockElement(text, attributes, getMarginHeading());
        start(text, new Heading(level));
    }

    private static void endHeading(Editable text) {
        // RelativeSizeSpan and StyleSpan are CharacterStyles
        // Their ranges should not include the newlines at the end
        Heading h = getLast(text, Heading.class);
        if (h != null) {
            setSpanFromMark(text, h, new RelativeSizeSpan(HEADING_SIZES[h.mLevel]),
                    new StyleSpan(Typeface.BOLD));
        }

        endBlockElement(text);
    }

    private static <T> T getLast(Spanned text, Class<T> kind) {
        /*
         * This knows that the last returned object from getSpans()
         * will be the most recently added.
         */
        T[] objs = text.getSpans(0, text.length(), kind);

        if (objs.length == 0) {
            return null;
        } else {
            return objs[objs.length - 1];
        }
    }

    private static void setSpanFromMark(Spannable text, Object mark, Object... spans) {
        int where = text.getSpanStart(mark);
        text.removeSpan(mark);
        int len = text.length();
        if (where != len) {
            for (Object span : spans) {
                text.setSpan(span, where, len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private static void start(Editable text, Object mark) {
        int len = text.length();
        text.setSpan(mark, len, len, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
    }

    private static void end(Editable text, Class kind, Object repl) {
        int len = text.length();
        Object obj = getLast(text, kind);
        if (obj != null) {
            setSpanFromMark(text, obj, repl);
        }
    }

    private void startCssStyle(Editable text, Attributes attributes) {
        String style = attributes.getValue("", "style");
        if (style != null) {
            Matcher m = getForegroundColorPattern().matcher(style);
            if (m.find()) {
                int c = getHtmlColor(m.group(1));
                if (c != -1) {
                    start(text, new Foreground(c | 0xFF000000));
                }
            }

            m = getBackgroundColorPattern().matcher(style);
            if (m.find()) {
                int c = getHtmlColor(m.group(1));
                if (c != -1) {
                    start(text, new Background(c | 0xFF000000));
                }
            }

            m = getTextDecorationPattern().matcher(style);
            if (m.find()) {
                String textDecoration = m.group(1);
                if (textDecoration.equalsIgnoreCase("line-through")) {
                    start(text, new Strikethrough());
                }
            }
        }
    }

    private static void endCssStyle(Editable text) {
        Strikethrough s = getLast(text, Strikethrough.class);
        if (s != null) {
            setSpanFromMark(text, s, new StrikethroughSpan());
        }

        Background b = getLast(text, Background.class);
        if (b != null) {
            setSpanFromMark(text, b, new BackgroundColorSpan(b.mBackgroundColor));
        }

        Foreground f = getLast(text, Foreground.class);
        if (f != null) {
            setSpanFromMark(text, f, new ForegroundColorSpan(f.mForegroundColor));
        }
    }

    private static void startImg(Editable text, Attributes attributes, Html.ImageGetter img) {
        String src = attributes.getValue("", "src");
        String width = attributes.getValue("", "width");
        String height = attributes.getValue("", "height");
        int h = 0;
        int w = 0;
        if (width != null) {
            w = Integer.parseInt(width);
        }
        if (height != null) {
            h = Integer.parseInt(height);
        }
        Drawable d = null;

        if (img != null) {
            d = img.getDrawable(src, w, h);
        }

        if (d != null) {
            int len = text.length();
            text.append("\uFFFC");

            text.setSpan(new ImageSpan(d, src), len, text.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static Stack<String> sizeStack;
    private static Stack<Integer> startIndexStack;


    private void startFont(Editable text, Attributes attributes) {
        String color = attributes.getValue("", "color");
        String face = attributes.getValue("", "face");
        String size = attributes.getValue("", "size");

        if (!TextUtils.isEmpty(color)) {
            int c = getHtmlColor(color);
            if (c != -1) {
                start(text, new Foreground(c | 0xFF000000));
            }
        }

        if (!TextUtils.isEmpty(face)) {
            start(text, new Font(face));
        }

        if (startIndexStack == null) {
            startIndexStack = new Stack<>();
        }
        if (sizeStack == null) {
            sizeStack = new Stack<>();
        }

        startIndexStack.push(text.length());
        if (!TextUtils.isEmpty(size)) {
            sizeStack.push(size);
        }
    }

    private static void endFont(Editable text) {
        Font font = getLast(text, Font.class);
        if (font != null) {
            setSpanFromMark(text, font, new TypefaceSpan(font.mFace));
        }

        Foreground foreground = getLast(text, Foreground.class);
        if (foreground != null) {
            setSpanFromMark(text, foreground,
                    new ForegroundColorSpan(foreground.mForegroundColor));
        }


        int startIndex = startIndexStack.pop();
        if (!isEmpty(sizeStack)) {
            String size = sizeStack.pop();
            if (!TextUtils.isEmpty(size)) {
                text.setSpan(new AbsoluteSizeSpan(Integer.parseInt(size)), startIndex, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    public static boolean isEmpty(Collection collection) {
        return collection == null || collection.isEmpty();
    }

    private static void startA(Editable text, Attributes attributes) {
        String href = attributes.getValue("", "href");
        start(text, new Href(href));
    }

    private static void endA(Editable text) {
        Href h = getLast(text, Href.class);
        if (h != null) {
            if (h.mHref != null) {
                setSpanFromMark(text, h, new URLSpan((h.mHref)));
            }
        }
    }

    private int getHtmlColor(String color) {
        if ((mFlags & android.text.Html.FROM_HTML_OPTION_USE_CSS_COLORS)
                == android.text.Html.FROM_HTML_OPTION_USE_CSS_COLORS) {
            Integer i = sColorMap.get(color.toLowerCase(Locale.US));
            if (i != null) {
                return i;
            }
        }
        return getColor(color);
    }

    private static final HashMap<String, Integer> sColorNameMap;

    public static final int BLACK = 0xFF000000;
    public static final int DKGRAY = 0xFF444444;
    public static final int GRAY = 0xFF888888;
    public static final int LTGRAY = 0xFFCCCCCC;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int RED = 0xFFFF0000;
    public static final int GREEN = 0xFF00FF00;
    public static final int BLUE = 0xFF0000FF;
    public static final int YELLOW = 0xFFFFFF00;
    public static final int CYAN = 0xFF00FFFF;
    public static final int MAGENTA = 0xFFFF00FF;
    public static final int ALICEBLUE = 0xFFF0F8FF;
    public static final int ANTIQUEWHITE = 0xFFFAEBD7;
    public static final int AQUA = 0xFF00FFFF;
    public static final int AQUAMARINE = 0xFF7FFFD4;
    public static final int AZURE = 0xFFF0FFFF;
    public static final int BEIGE = 0xFFF5F5DC;
    public static final int BISQUE = 0xFFFFE4C4;
    public static final int BLANCHEDALMOND = 0xFFFFEBCD;
    public static final int BLUEVIOLET = 0xFF8A2BE2;
    public static final int BROWN = 0xFFA52A2A;
    public static final int BURLYWOOD = 0xFFDEB887;
    public static final int CADETBLUE = 0xFF5F9EA0;
    public static final int CHARTREUSE = 0xFF7FFF00;
    public static final int CHOCOLATE = 0xFFD2691E;
    public static final int CORAL = 0xFFFF7F50;
    public static final int CORNFLOWERBLUE = 0xFF6495ED;
    public static final int CORNSILK = 0xFFFFF8DC;
    public static final int CRIMSON = 0xFFDC143C;
    public static final int DARKBLUE = 0xFF00008B;
    public static final int DARKCYAN = 0xFF008B8B;
    public static final int DARKGOLDENROD = 0xFFB8860B;
    public static final int DARKGRAY = 0xFFA9A9A9;
    public static final int DARKGREY = 0xFFA9A9A9;
    public static final int DARKGREEN = 0xFF006400;
    public static final int DARKKHAKI = 0xFFBDB76B;
    public static final int DARKMAGENTA = 0xFF8B008B;
    public static final int DARKOLIVEGREEN = 0xFF556B2F;
    public static final int DARKORANGE = 0xFFFF8C00;
    public static final int DARKORCHID = 0xFF9932CC;
    public static final int DARKRED = 0xFF8B0000;
    public static final int DARKSALMON = 0xFFE9967A;
    public static final int DARKSEAGREEN = 0xFF8FBC8F;
    public static final int DARKSLATEBLUE = 0xFF483D8B;
    public static final int DARKSLATEGRAY = 0xFF2F4F4F;
    public static final int DARKSLATEGREY = 0xFF2F4F4F;
    public static final int DARKTURQUOISE = 0xFF00CED1;
    public static final int DARKVIOLET = 0xFF9400D3;
    public static final int DEEPPINK = 0xFFFF1493;
    public static final int DEEPSKYBLUE = 0xFF00BFFF;
    public static final int DIMGRAY = 0xFF696969;
    public static final int DIMGREY = 0xFF696969;
    public static final int DODGERBLUE = 0xFF1E90FF;
    public static final int FIREBRICK = 0xFFB22222;
    public static final int FLORALWHITE = 0xFFFFFAF0;
    public static final int FORESTGREEN = 0xFF228B22;
    public static final int FUCHSIA = 0xFFFF00FF;
    public static final int GAINSBORO = 0xFFDCDCDC;
    public static final int GHOSTWHITE = 0xFFF8F8FF;
    public static final int GOLD = 0xFFFFD700;
    public static final int GOLDENROD = 0xFFDAA520;
    public static final int GREY = 0xFF808080;
    public static final int GREENYELLOW = 0xFFADFF2F;
    public static final int HONEYDEW = 0xFFF0FFF0;
    public static final int HOTPINK = 0xFFFF69B4;
    public static final int INDIANRED = 0xFFCD5C5C;
    public static final int INDIGO = 0xFF4B0082;
    public static final int IVORY = 0xFFFFFFF0;
    public static final int KHAKI = 0xFFF0E68C;
    public static final int LAVENDER = 0xFFE6E6FA;
    public static final int LAVENDERBLUSH = 0xFFFFF0F5;
    public static final int LAWNGREEN = 0xFF7CFC00;
    public static final int LEMONCHIFFON = 0xFFFFFACD;
    public static final int LIGHTBLUE = 0xFFADD8E6;
    public static final int LIGHTCORAL = 0xFFF08080;
    public static final int LIGHTCYAN = 0xFFE0FFFF;
    public static final int LIGHTGOLDENRODYELLOW = 0xFFFAFAD2;
    public static final int LIGHTGRAY = 0xFFD3D3D3;
    public static final int LIGHTGREY = 0xFFD3D3D3;
    public static final int LIGHTGREEN = 0xFF90EE90;
    public static final int LIGHTPINK = 0xFFFFB6C1;
    public static final int LIGHTSALMON = 0xFFFFA07A;
    public static final int LIGHTSEAGREEN = 0xFF20B2AA;
    public static final int LIGHTSKYBLUE = 0xFF87CEFA;
    public static final int LIGHTSLATEGRAY = 0xFF778899;
    public static final int LIGHTSLATEGREY = 0xFF778899;
    public static final int LIGHTSTEELBLUE = 0xFFB0C4DE;
    public static final int LIGHTYELLOW = 0xFFFFFFE0;
    public static final int LIME = 0xFF00FF00;
    public static final int LIMEGREEN = 0xFF32CD32;
    public static final int LINEN = 0xFFFAF0E6;
    public static final int MAROON = 0xFF800000;
    public static final int MEDIUMAQUAMARINE = 0xFF66CDAA;
    public static final int MEDIUMBLUE = 0xFF0000CD;
    public static final int MEDIUMORCHID = 0xFFBA55D3;
    public static final int MEDIUMPURPLE = 0xFF9370D8;
    public static final int MEDIUMSEAGREEN = 0xFF3CB371;
    public static final int MEDIUMSLATEBLUE = 0xFF7B68EE;
    public static final int MEDIUMSPRINGGREEN = 0xFF00FA9A;
    public static final int MEDIUMTURQUOISE = 0xFF48D1CC;
    public static final int MEDIUMVIOLETRED = 0xFFC71585;
    public static final int MIDNIGHTBLUE = 0xFF191970;
    public static final int MINTCREAM = 0xFFF5FFFA;
    public static final int MISTYROSE = 0xFFFFE4E1;
    public static final int MOCCASIN = 0xFFFFE4B5;
    public static final int NAVAJOWHITE = 0xFFFFDEAD;
    public static final int NAVY = 0xFF000080;
    public static final int OLDLACE = 0xFFFDF5E6;
    public static final int OLIVE = 0xFFFDF5E6;
    public static final int OLIVEDRAB = 0xFF6B8E23;
    public static final int ORANGE = 0xFFFFA500;
    public static final int ORANGERED = 0xFFFF4500;
    public static final int ORCHID = 0xFFDA70D6;
    public static final int PALEGOLDENROD = 0xFFEEE8AA;
    public static final int PALEGREEN = 0xFF98FB98;
    public static final int PALETURQUOISE = 0xFFAFEEEE;
    public static final int PALEVIOLETRED = 0xFFD87093;
    public static final int PAPAYAWHIP = 0xFFFFEFD5;
    public static final int PEACHPUFF = 0xFFFFDAB9;
    public static final int PERU = 0xFFCD853F;
    public static final int PINK = 0xFFFFC0CB;
    public static final int PLUM = 0xFFDDA0DD;
    public static final int POWDERBLUE = 0xFFB0E0E6;
    public static final int PURPLE = 0xFF800080;
    public static final int ROSYBROWN = 0xFFBC8F8F;
    public static final int ROYALBLUE = 0xFF4169E1;
    public static final int SADDLEBROWN = 0xFF8B4513;
    public static final int SALMON = 0xFFFA8072;
    public static final int SANDYBROWN = 0xFFF4A460;
    public static final int SEAGREEN = 0xFF2E8B57;
    public static final int SEASHELL = 0xFFFFF5EE;
    public static final int SIENNA = 0xFFA0522D;
    public static final int SILVER = 0xFFC0C0C0;
    public static final int SKYBLUE = 0xFF87CEEB;
    public static final int SLATEBLUE = 0xFF6A5ACD;
    public static final int SLATEGRAY = 0xFF708090;
    public static final int SLATEGREY = 0xFF708090;
    public static final int SNOW = 0xFFFFFAFA;
    public static final int SPRINGGREEN = 0xFF00FF7F;
    public static final int STEELBLUE = 0xFF4682B4;
    public static final int TAN = 0xFFD2B48C;
    public static final int TEAL = 0xFF008080;
    public static final int THISTLE = 0xFFD8BFD8;
    public static final int TOMATO = 0xFFFF6347;
    public static final int TURQUOISE = 0xFFFF6347;
    public static final int VIOLET = 0xFFEE82EE;
    public static final int WHEAT = 0xFFF5DEB3;
    public static final int WHITESMOKE = 0xFFF5F5F5;
    public static final int YELLOWGREEN = 0xFF9ACD32;

    static {
        sColorNameMap = new HashMap<String, Integer>();
        sColorNameMap.put("black", BLACK);
        sColorNameMap.put("darkgray", DKGRAY);
        sColorNameMap.put("gray", GRAY);
        sColorNameMap.put("lightgray", LTGRAY);
        sColorNameMap.put("white", WHITE);
        sColorNameMap.put("red", RED);
        sColorNameMap.put("green", GREEN);
        sColorNameMap.put("blue", BLUE);
        sColorNameMap.put("yellow", YELLOW);
        sColorNameMap.put("cyan", CYAN);
        sColorNameMap.put("magenta", MAGENTA);
        sColorNameMap.put("aliceblue", ALICEBLUE);
        sColorNameMap.put("antiquewhite", ANTIQUEWHITE);
        sColorNameMap.put("aqua", AQUA);
        sColorNameMap.put("aquamarine", AQUAMARINE);
        sColorNameMap.put("azure", AZURE);
        sColorNameMap.put("beige", BEIGE);
        sColorNameMap.put("bisque", BISQUE);
        sColorNameMap.put("blanchedalmond", BLANCHEDALMOND);
        sColorNameMap.put("blueviolet", BLUEVIOLET);
        sColorNameMap.put("brown", BROWN);
        sColorNameMap.put("burlywood", BURLYWOOD);
        sColorNameMap.put("cadetblue", CADETBLUE);
        sColorNameMap.put("chartreuse", CHARTREUSE);
        sColorNameMap.put("chocolate", CHOCOLATE);
        sColorNameMap.put("coral", CORAL);
        sColorNameMap.put("cornflowerblue", CORNFLOWERBLUE);
        sColorNameMap.put("cornsilk", CORNSILK);
        sColorNameMap.put("crimson", CRIMSON);
        sColorNameMap.put("darkblue", DARKBLUE);
        sColorNameMap.put("darkcyan", DARKCYAN);
        sColorNameMap.put("darkgoldenrod", DARKGOLDENROD);
        sColorNameMap.put("darkgray", DARKGRAY);
        sColorNameMap.put("darkgrey", DARKGREY);
        sColorNameMap.put("darkgreen", DARKGREEN);
        sColorNameMap.put("darkkhaki", DARKKHAKI);
        sColorNameMap.put("darkmagenta", DARKMAGENTA);
        sColorNameMap.put("darkolivegreen", DARKOLIVEGREEN);
        sColorNameMap.put("darkorange", DARKORANGE);
        sColorNameMap.put("darkorchid", DARKORCHID);
        sColorNameMap.put("darkred", DARKRED);
        sColorNameMap.put("darksalmon", DARKSALMON);
        sColorNameMap.put("darkseagreen", DARKSEAGREEN);
        sColorNameMap.put("darkslateblue", DARKSLATEBLUE);
        sColorNameMap.put("darkslategray", DARKSLATEGRAY);
        sColorNameMap.put("darkslategrey", DARKSLATEGREY);
        sColorNameMap.put("darkturquoise", DARKTURQUOISE);
        sColorNameMap.put("darkviolet", DARKVIOLET);
        sColorNameMap.put("deeppink", DEEPPINK);
        sColorNameMap.put("deepskyblue", DEEPSKYBLUE);
        sColorNameMap.put("dimgray", DIMGRAY);
        sColorNameMap.put("dimgrey", DIMGREY);
        sColorNameMap.put("dodgerblue", DODGERBLUE);
        sColorNameMap.put("firebrick", FIREBRICK);
        sColorNameMap.put("floralwhite", FLORALWHITE);
        sColorNameMap.put("forestgreen", FORESTGREEN);
        sColorNameMap.put("fuchsia", FUCHSIA);
        sColorNameMap.put("gainsboro", GAINSBORO);
        sColorNameMap.put("ghostwhite", GHOSTWHITE);
        sColorNameMap.put("gold", GOLD);
        sColorNameMap.put("goldenrod", GOLDENROD);
        sColorNameMap.put("grey", GREY);
        sColorNameMap.put("greenyellow", GREENYELLOW);
        sColorNameMap.put("honeydew", HONEYDEW);
        sColorNameMap.put("hotpink", HOTPINK);
        sColorNameMap.put("indianred", INDIANRED);
        sColorNameMap.put("indigo", INDIGO);
        sColorNameMap.put("ivory", IVORY);
        sColorNameMap.put("khaki", KHAKI);
        sColorNameMap.put("lavender", LAVENDER);
        sColorNameMap.put("lavenderblush", LAVENDERBLUSH);
        sColorNameMap.put("lawngreen", LAWNGREEN);
        sColorNameMap.put("lemonchiffon", LEMONCHIFFON);
        sColorNameMap.put("lightblue", LIGHTBLUE);
        sColorNameMap.put("lightcoral", LIGHTCORAL);
        sColorNameMap.put("lightcyan", LIGHTCYAN);
        sColorNameMap.put("lightgoldenrodyellow", LIGHTGOLDENRODYELLOW);
        sColorNameMap.put("lightgray", LIGHTGRAY);
        sColorNameMap.put("lightgrey", LIGHTGREY);
        sColorNameMap.put("lightgreen", LIGHTGREEN);
        sColorNameMap.put("lightpink", LIGHTPINK);
        sColorNameMap.put("lightsalmon", LIGHTSALMON);
        sColorNameMap.put("lightseagreen", LIGHTSEAGREEN);
        sColorNameMap.put("lightskyblue", LIGHTSKYBLUE);
        sColorNameMap.put("lightslategray", LIGHTSLATEGRAY);
        sColorNameMap.put("lightslategrey", LIGHTSLATEGREY);
        sColorNameMap.put("lightsteelblue", LIGHTSTEELBLUE);
        sColorNameMap.put("lightyellow", LIGHTYELLOW);
        sColorNameMap.put("lime", LIME);
        sColorNameMap.put("limegreen", LIMEGREEN);
        sColorNameMap.put("linen", LINEN);
        sColorNameMap.put("maroon", MAROON);
        sColorNameMap.put("mediumaquamarine", MEDIUMAQUAMARINE);
        sColorNameMap.put("mediumblue", MEDIUMBLUE);
        sColorNameMap.put("mediumorchid", MEDIUMORCHID);
        sColorNameMap.put("mediumpurple", MEDIUMPURPLE);
        sColorNameMap.put("mediumseagreen", MEDIUMSEAGREEN);
        sColorNameMap.put("mediumslateblue", MEDIUMSLATEBLUE);
        sColorNameMap.put("mediumspringgreen", MEDIUMSPRINGGREEN);
        sColorNameMap.put("mediumturquoise", MEDIUMTURQUOISE);
        sColorNameMap.put("mediumvioletred", MEDIUMVIOLETRED);
        sColorNameMap.put("midnightblue", MIDNIGHTBLUE);
        sColorNameMap.put("mintcream", MINTCREAM);
        sColorNameMap.put("mistyrose", MISTYROSE);
        sColorNameMap.put("moccasin", MOCCASIN);
        sColorNameMap.put("navajowhite", NAVAJOWHITE);
        sColorNameMap.put("navy", NAVY);
        sColorNameMap.put("oldlace", OLDLACE);
        sColorNameMap.put("olive", OLIVE);
        sColorNameMap.put("olivedrab", OLIVEDRAB);
        sColorNameMap.put("orange", ORANGE);
        sColorNameMap.put("orangered", ORANGERED);
        sColorNameMap.put("orchid", ORCHID);
        sColorNameMap.put("palegoldenrod", PALEGOLDENROD);
        sColorNameMap.put("palegreen", PALEGREEN);
        sColorNameMap.put("paleturquoise", PALETURQUOISE);
        sColorNameMap.put("palevioletred", PALEVIOLETRED);
        sColorNameMap.put("papayawhip", PAPAYAWHIP);
        sColorNameMap.put("peachpuff", PEACHPUFF);
        sColorNameMap.put("peru", PERU);
        sColorNameMap.put("pink", PINK);
        sColorNameMap.put("plum", PLUM);
        sColorNameMap.put("powderblue", POWDERBLUE);
        sColorNameMap.put("purple", PURPLE);
        sColorNameMap.put("rosybrown", ROSYBROWN);
        sColorNameMap.put("royalblue", ROYALBLUE);
        sColorNameMap.put("saddlebrown", SADDLEBROWN);
        sColorNameMap.put("salmon", SALMON);
        sColorNameMap.put("sandybrown", SANDYBROWN);
        sColorNameMap.put("seagreen", SEAGREEN);
        sColorNameMap.put("seashell", SEASHELL);
        sColorNameMap.put("sienna", SIENNA);
        sColorNameMap.put("silver", SILVER);
        sColorNameMap.put("skyblue", SKYBLUE);
        sColorNameMap.put("slateblue", SLATEBLUE);
        sColorNameMap.put("slategray", SLATEGRAY);
        sColorNameMap.put("slategrey", SLATEGREY);
        sColorNameMap.put("snow", SNOW);
        sColorNameMap.put("springgreen", SPRINGGREEN);
        sColorNameMap.put("steelblue", STEELBLUE);
        sColorNameMap.put("tan", TAN);
        sColorNameMap.put("teal", TEAL);
        sColorNameMap.put("thistle", THISTLE);
        sColorNameMap.put("tomato", TOMATO);
        sColorNameMap.put("turquoise", TURQUOISE);
        sColorNameMap.put("violet", VIOLET);
        sColorNameMap.put("wheat", WHEAT);
        sColorNameMap.put("whitesmoke", WHITESMOKE);
        sColorNameMap.put("yellowgreen", YELLOWGREEN);
    }

    public static int getColor(@NonNull String color) {
        Integer i = sColorNameMap.get(color.toLowerCase(Locale.ROOT));
        if (i != null) {
            return i;
        } else {
                return -1;
        }
    }

    public void setDocumentLocator(Locator locator) {
    }

    public void startDocument() throws SAXException {
    }

    public void endDocument() throws SAXException {
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
    }

    public void endPrefixMapping(String prefix) throws SAXException {
    }

    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        handleStartTag(localName, attributes);
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        handleEndTag(localName);
    }

    public void characters(char ch[], int start, int length) throws SAXException {
        StringBuilder sb = new StringBuilder();

        /*
         * Ignore whitespace that immediately follows other whitespace;
         * newlines count as spaces.
         */

        for (int i = 0; i < length; i++) {
            char c = ch[i + start];

            if (c == ' ' || c == '\n') {
                char pred;
                int len = sb.length();

                if (len == 0) {
                    len = mSpannableStringBuilder.length();

                    if (len == 0) {
                        pred = '\n';
                    } else {
                        pred = mSpannableStringBuilder.charAt(len - 1);
                    }
                } else {
                    pred = sb.charAt(len - 1);
                }

                if (pred != ' ' && pred != '\n') {
                    sb.append(' ');
                }
            } else {
                sb.append(c);
            }
        }

        mSpannableStringBuilder.append(sb);
    }

    public void ignorableWhitespace(char ch[], int start, int length) throws SAXException {
    }

    public void processingInstruction(String target, String data) throws SAXException {
    }

    public void skippedEntity(String name) throws SAXException {
    }

    private static class Bold {
    }

    private static class Italic {
    }

    private static class Underline {
    }

    private static class Strikethrough {
    }

    private static class Big {
    }

    private static class Small {
    }

    private static class Monospace {
    }

    private static class Blockquote {
    }

    private static class Super {
    }

    private static class Sub {
    }

    private static class Bullet {
    }

    private static class Font {
        public String mFace;

        public Font(String face) {
            mFace = face;
        }
    }

    private static class Href {
        public String mHref;

        public Href(String href) {
            mHref = href;
        }
    }

    private static class Foreground {
        private int mForegroundColor;

        public Foreground(int foregroundColor) {
            mForegroundColor = foregroundColor;
        }
    }

    private static class Background {
        private int mBackgroundColor;

        public Background(int backgroundColor) {
            mBackgroundColor = backgroundColor;
        }
    }

    private static class Heading {
        private int mLevel;

        public Heading(int level) {
            mLevel = level;
        }
    }

    private static class Newline {
        private int mNumNewlines;

        public Newline(int numNewlines) {
            mNumNewlines = numNewlines;
        }
    }

    private static class Alignment {
        private Layout.Alignment mAlignment;

        public Alignment(Layout.Alignment alignment) {
            mAlignment = alignment;
        }
    }
}
