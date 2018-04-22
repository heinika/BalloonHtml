🎈🎈🎈🎈🎈🎈  
BalloonHtml can get Spenned just like android.text.Html.

Use BalloonHtml replace android.text.Html to support font size and image width and height. 
 
\<font color="red" size="10">Balloon\</font>,  

\<img src="ic_launcher.png" width="50" height="50"/>

<img src="https://github.com/heinika/BalloonHtml/blob/master/Screenshot_BalloonHtml.png" width="40%" height="40%">



# Download
Gradle:

```groovy
repositories {
    maven {url 'https://dl.bintray.com/heinika/maven'}
}

dependencies {
    implementation 'com.github.heinika:balloonhtml:1.0.0'
}
```

Or Maven:
```groovy
<dependency>
  <groupId>com.github.heinika</groupId>
  <artifactId>balloonhtml</artifactId>
  <version>1.0.0</version>
  <type>pom</type>
</dependency>
```

# How do I use BalloomHtml?

Simple use cases with BalloomHtml will look like this:
```java
    final TextView textView = findViewById(R.id.textView);
    String html = "<img src=\"ic_launcher.png\" width=\"30\" height=\"30\"/><br/> " +
            "<font color=\"red\" size=\"70\">Balloon</font><br/>" ;
    Spanned span = BalloonHtml.fromHtml(this,html, new BalloonHtml.ImageGetter() {
        @Override
        public Drawable getDrawable(String source) {
            return getResources().getDrawable(R.mipmap.ic_launcher_round);
        }
    });
    textView.setText(span);
```