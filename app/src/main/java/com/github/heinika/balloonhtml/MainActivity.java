package com.github.heinika.balloonhtml;

import android.graphics.drawable.Drawable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Spanned;
import android.widget.TextView;

import com.github.heinika.BalloonHtml;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final TextView textView = findViewById(R.id.textView);
        String html = "<img src=\"ic_launcher.png\" width=\"30\" height=\"30\"/><br/> " +
                "<img src=\"ic_launcher.png\" width=\"40\" height=\"40\"/><br/> "+
                "<img src=\"ic_launcher.png\" width=\"60\" height=\"60\"/><br/> "+
                "<img src=\"ic_launcher.png\" width=\"70\" height=\"70\"/><br/> "+
                "<img src=\"ic_launcher.png\" width=\"80\" height=\"80\"/><br/> "+
                "<font color=\"red\" size=\"20\">Balloon</font><br/>"+
                "<font color=\"red\" size=\"30\">Balloon</font><br/>"+
                "<font color=\"red\" size=\"40\">Balloon</font><br/>"+
                "<font color=\"red\" size=\"50\">Balloon</font><br/>"+
                "<font color=\"red\" size=\"60\">Balloon</font><br/>"+
                "<font color=\"red\" size=\"70\">Balloon</font><br/>" ;
        Spanned span = BalloonHtml.fromHtml(this,html, new BalloonHtml.ImageGetter() {
            @Override
            public Drawable getDrawable(String source) {
                return getResources().getDrawable(R.mipmap.ic_launcher_round);
            }
        });
        textView.setText(span);
    }
}
