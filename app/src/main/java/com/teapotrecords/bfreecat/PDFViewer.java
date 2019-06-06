package com.teapotrecords.bfreecat;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.WebView;
import android.widget.ImageView;

import com.sun.pdfview.PDFFile;
import com.sun.pdfview.PDFImage;
import com.sun.pdfview.PDFPage;
import com.sun.pdfview.PDFPaint;

import net.sf.andpdf.nio.ByteBuffer;
import net.sf.andpdf.refs.HardReference;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;

public class PDFViewer extends AppCompatActivity {

  private WebView wv;
  private int ViewSize = 0;
  private int current_page = 1;
  private int count_pages = 1;
  private double current_zoom = 1;
  private int current_x,current_y;
  private ArrayList<StringBuilder> pdf_pages = new ArrayList<StringBuilder>();

  private double req_zoom;
  private int req_x,req_y,req_page;
  private boolean saved=true;

  void savePrefs(String root, String officeno, String filetype) {
    try {
      if (new File(root,"pdf_prefs.txt").exists()) new File(root,"pdf_prefs.txt").delete();
      File f = new File(root,"pdf_prefs"+filetype+".txt");
      File f2 = new File(root,"pdf_prefs"+filetype+".tmp");
      BufferedReader br = new BufferedReader(new FileReader(f));
      PrintWriter PW = new PrintWriter(new FileWriter(f2));
      String s = br.readLine();
      String [] bits;
      boolean found=false;
      while (s!=null) {
        if (s.startsWith(officeno+"\t")) {
          found=true;
          PW.println(officeno+"\t"+String.valueOf(req_page)+"\t"+String.valueOf(req_zoom)+"\t"+String.valueOf(req_x)+"\t"+String.valueOf(req_y));
        } else PW.println(s);
        s=br.readLine();
      }
      br.close();
      if (!found) {
        PW.println(officeno+"\t"+String.valueOf(req_page)+"\t"+String.valueOf(req_zoom)+"\t"+String.valueOf(req_x)+"\t"+String.valueOf(req_y));
      }
      PW.close();
      f.delete();
      f2.renameTo(f);
      //((ImageView)findViewById(R.id.remember)).setImageResource(R.drawable.heart_grey);
      //saved=true;

    } catch (Exception e) { e.printStackTrace(); }
  }

  void loadPrefs(String root, String officeno, String filetype) {
    try {
      File f = new File(root,"pdf_prefs"+filetype+".txt");
      if (!f.exists()) {
        PrintWriter PW = new PrintWriter(new FileWriter(new File(root,"pdf_prefs"+filetype+".txt")));
        PW.close();
      }
      BufferedReader br = new BufferedReader(new FileReader(f));
      String s = br.readLine();
      String [] bits;
      boolean found=false;
      while (s!=null) {
        if (s.startsWith(officeno+"\t")) {
          found=true;
          bits = s.split("\t");
          req_page=Integer.parseInt(bits[1]);
          req_zoom=Double.parseDouble(bits[2]);
          req_x=Integer.parseInt(bits[3]);
          req_y=Integer.parseInt(bits[4]);
          s=null;
        } else s=br.readLine();
      }
      br.close();
      if (!found) {
        req_page=1;
        req_x=0;
        req_y=0;
        req_zoom=1;
      }
      saved=true;

    } catch (Exception e) { e.printStackTrace(); }

  }

  //OnCreate Method:
  @Override
  protected void onCreate(Bundle savedInstanceState) {

    super.onCreate(savedInstanceState);
    Bundle b = getIntent().getExtras();
    final String root = b.getString("root");
    final String officeno = b.getString("officeno");
    final String filetype = b.getString("filetype");
    File thePDF = new File(root, b.getString("pdffile"));
    setContentView(R.layout.activity_pdfviewer);
    LayoutInflater inflater = (LayoutInflater) getSupportActionBar().getThemedContext().getSystemService(LAYOUT_INFLATER_SERVICE);
    View customActionBarView = inflater.inflate(R.layout.actionbarpdf_custom, null);

    /* Look up any preferences for this song */

    loadPrefs(root, officeno,filetype);

    ActionBar actionBar = getSupportActionBar();
    actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM, ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
    actionBar.setCustomView(customActionBarView, new ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    actionBar.setTitle("");
    actionBar.setBackgroundDrawable(new ColorDrawable(Color.WHITE));

    //Settings
    PDFImage.sShowImages = true; // show images
    PDFPaint.s_doAntiAlias = true; // make text smooth
    HardReference.sKeepCaches = true; // save images in cache
    ImageView previous = (ImageView) findViewById(R.id.previous);
    previous.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        if (current_page > 1) {
          current_page--;
          PDFViewer.this.runOnUiThread(updatePages);
        }
      }
    });

    ImageView next = (ImageView) findViewById(R.id.next);
    next.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        if (current_page < count_pages) {
          current_page++;
          PDFViewer.this.runOnUiThread(updatePages);
        }
      }
    });

    ImageView favourite = (ImageView) findViewById(R.id.remember);
    favourite.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        PDFViewer.this.runOnUiThread(new Runnable() {
          public void run() {
            ((ImageView)findViewById(R.id.remember)).setImageResource(R.drawable.heart_grey);
            req_page=current_page;
            req_x = wv.getScrollX();
            req_y = wv.getScrollY();
            req_zoom = wv.getScale(); // Deprecated, yes I know.
            savePrefs(root, officeno,filetype);
          }
        });
      }
    });


        //System.out.println("Zoom = "+wv.getScale());
        //System.out.println("Scroll = "+wv.getScrollX()+","+wv.getScrollY());
    //  }
    //});



    wv = (WebView) findViewById(R.id.theviewer);
    wv.getSettings().setBuiltInZoomControls(true);//show zoom buttons
    wv.getSettings().setSupportZoom(true);//allow zoom
    wv.getSettings().setUseWideViewPort(true);
    //wv.setOnTouchListener(touchWebView);

    //get the width of the webview

    wv.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
      @Override
      public void onGlobalLayout() {
        ViewSize = wv.getWidth();
        wv.getViewTreeObserver().removeGlobalOnLayoutListener(this);
      }
    });

    try {
      RandomAccessFile f = new RandomAccessFile(thePDF, "r");
      byte[] data = new byte[(int) f.length()];
      f.readFully(data);
      pdfLoadImages(data);
    } catch (Exception ignored) {
    }
  }

  //Load Images:
  private void pdfLoadImages(final byte[] data) {
    try {
      // run async
      new AsyncTask<Void, Void, String>() {
        @Override
        protected void onPostExecute(String html) {
          updatePages.run();
          wv.setScrollX(req_x);
          wv.setScrollY(req_y);
          wv.setInitialScale((int)(100*req_zoom));
        }

        @Override
        protected String doInBackground(Void... params) {
          try {
            ByteBuffer bb = ByteBuffer.NEW(data);
            PDFFile pdf = new PDFFile(bb);
            count_pages=pdf.getNumPages();
            current_page=Math.min(req_page,count_pages);
            while (pdf_pages.size()<count_pages) pdf_pages.add(new StringBuilder());
            for (int i=0; i<count_pages; i++) {
              pdf_pages.get(i).setLength(0);
              PDFPage PDFpage = pdf.getPage(i + 1, true);
              final float scale = ViewSize / PDFpage.getWidth() * 0.99f;
              Bitmap page = PDFpage.getImage((int) (PDFpage.getWidth() * scale), (int) (PDFpage.getHeight() * scale), null, true, true);
              ByteArrayOutputStream stream = new ByteArrayOutputStream();
              page.compress(Bitmap.CompressFormat.PNG, 100, stream);
              byte[] byteArray = stream.toByteArray();
              stream.reset();
              String base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP);
              pdf_pages.get(i).append("<img src=\"data:image/png;base64," + base64 + "\"/><br/>");
              stream.close();
            }

          } catch (Exception e) {
            Log.d("error", e.toString());
          }
          return null;
        }
      }.execute();
      System.gc();// run GC
    } catch (Exception e) {
      Log.d("error", e.toString());
    }
  }

  Runnable updatePages = new Runnable() {
    public void run() {

      ((ImageView)findViewById(R.id.previous)).setImageResource(current_page>1?R.drawable.left:R.drawable.left_grey);
      ((ImageView)findViewById(R.id.next)).setImageResource(current_page<count_pages?R.drawable.right:R.drawable.right_grey);
      String html = "<!DOCTYPE html><html>";
      html+="<meta name=\"viewport\" content=\"width="+ViewSize+", initial-scale=1\"/>";
      html+="<body bgcolor=\"#b4b4b4\">"+pdf_pages.get(current_page-1).toString();
      html+="</body></html>";
      wv.loadDataWithBaseURL("", html, "text/html", "UTF-8", "");
      ((ImageView)findViewById(R.id.remember)).setImageResource(R.drawable.heart);

    }
  };


  //View.OnTouchListener touchWebView = new View.OnTouchListener() {
//    public boolean onTouch(View v, MotionEvent mv) {
//      WebView w = (WebView) v;
//      current_x=w.getScrollX();
//      current_y=w.getScrollY();
//      current_zoom=w.getScale();
      //saved=((req_page==current_page) && (req_x==current_x) && (req_y==current_y) && (req_zoom==current_zoom));
      //((ImageView)findViewById(R.id.remember)).setImageResource(saved?R.drawable.heart_grey:R.drawable.heart);
      //System.out.println("Touch - "+w.getScrollX()+","+w.getScrollY()+","+w.getScale());
//      return true;
//    }
//  };

}