package com.teapotrecords.bfreecat;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
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
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class PDFViewer extends AppCompatActivity {

  private WebView wv;
  private int ViewSize = 0;
  private int current_page = 1;
  private int count_pages = 1;
  //private double current_zoom = 1;
  //private int current_x,current_y;
  private ArrayList<StringBuilder> pdf_pages = new ArrayList<>();

  private double req_zoom;
  private int req_x,req_y,req_page;
  //private boolean saved=true;

  void savePrefs(String root, String officeno, String filetype) {
    try {
      if (new File(root,"pdf_prefs.txt").exists()) new File(root,"pdf_prefs.txt").delete();
      File f = new File(root,"pdf_prefs"+filetype+".txt");
      File f2 = new File(root,"pdf_prefs"+filetype+".tmp");
      BufferedReader br = new BufferedReader(new FileReader(f));
      PrintWriter PW = new PrintWriter(new FileWriter(f2));
      String s = br.readLine();
      boolean found=false;
      while (s!=null) {
        if (s.startsWith(officeno+"\t")) {
          found=true;
          PW.println(officeno+"\t"+req_page+"\t"+req_zoom+"\t"+req_x+"\t"+req_y);
        } else PW.println(s);
        s=br.readLine();
      }
      br.close();
      if (!found) {
        PW.println(officeno+"\t"+req_page+"\t"+req_zoom+"\t"+req_x+"\t"+req_y);
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
      //saved=true;

    } catch (Exception e) { e.printStackTrace(); }

  }

  //OnCreate Method:
  @Override
  protected void onCreate(Bundle savedInstanceState) {

    super.onCreate(savedInstanceState);


    /* Look up any preferences for this song */
    Bundle b = getIntent().getExtras();

    final String root = (b != null) ? b.getString("root") : null;
    final String officeno = (b != null) ? b.getString("officeno") : null;
    final String filetype = (b != null) ? b.getString("filetype") : null;
    final File thePDF = (b != null) ? new File(root, b.getString("pdffile")) : null;

    loadPrefs(root, officeno, filetype);

    setContentView(R.layout.activity_pdfviewer);
    ActionBar actionBar = getSupportActionBar();
    if (actionBar!=null) {
      LayoutInflater inflater = (LayoutInflater) actionBar.getThemedContext().getSystemService(LAYOUT_INFLATER_SERVICE);
      @SuppressLint("InflateParams") View customActionBarView = inflater.inflate(R.layout.actionbarpdf_custom, null);



      actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM, ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
      actionBar.setCustomView(customActionBarView, new ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      actionBar.setTitle("");
      actionBar.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
    }
      //Settings

      PDFImage.sShowImages = true; // show images
      PDFPaint.s_doAntiAlias = true; // make text smooth
      HardReference.sKeepCaches = true; // save images in cache
      ImageView previous = findViewById(R.id.previous);
      previous.setOnClickListener(v -> {
        if (current_page > 1) {
          current_page--;
          PDFViewer.this.runOnUiThread(updatePages);
        }
      });

      ImageView next = findViewById(R.id.next);
      next.setOnClickListener(v -> {
        if (current_page < count_pages) {
          current_page++;
          PDFViewer.this.runOnUiThread(updatePages);
        }
      });

      ImageView favourite = findViewById(R.id.remember);
      favourite.setOnClickListener(v -> PDFViewer.this.runOnUiThread(() -> {
        ((ImageView)findViewById(R.id.remember)).setImageResource(R.drawable.heart_grey);
        req_page=current_page;
        req_x = wv.getScrollX();
        req_y = wv.getScrollY();
        req_zoom = wv.getScale(); // Deprecated, yes I know.
        savePrefs(root, officeno,filetype);
      }));

    wv = findViewById(R.id.theviewer);
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

  private static class PDFAsyncTask extends AsyncTask<Void, Void, String> {
    private WeakReference<PDFViewer> pdfActivityReference;
    byte[] data;

    PDFAsyncTask(PDFViewer context, byte[] _data) {
      super();
      data = _data;
      pdfActivityReference = new WeakReference<>(context);
    }

    protected void onPostExecute(java.lang.String html) {
      PDFViewer PDF = pdfActivityReference.get();
      if (PDF == null || PDF.isFinishing()) return;

      PDF.updatePages.run();
      PDF.wv.setScrollX(PDF.req_x);
      PDF.wv.setScrollY(PDF.req_y);
      PDF.wv.setInitialScale((int)(100*PDF.req_zoom));
    }

    protected java.lang.String doInBackground(java.lang.Void... params) {
      PDFViewer PDF = pdfActivityReference.get();
      if (PDF != null && !PDF.isFinishing()) try {

        ByteBuffer bb = ByteBuffer.NEW(data);
        PDFFile pdf = new PDFFile(bb);
        PDF.count_pages=pdf.getNumPages();
        PDF.current_page=Math.min(PDF.req_page,PDF.count_pages);
        while (PDF.pdf_pages.size()<PDF.count_pages) PDF.pdf_pages.add(new StringBuilder());
        for (int i=0; i<PDF.count_pages; i++) {
          PDF.pdf_pages.get(i).setLength(0);
          PDFPage PDFpage = pdf.getPage(i + 1, true);
          final float scale = PDF.ViewSize / PDFpage.getWidth() * 0.99f;
          Bitmap page = PDFpage.getImage((int) (PDFpage.getWidth() * scale), (int) (PDFpage.getHeight() * scale), null, true, true);
          ByteArrayOutputStream stream = new ByteArrayOutputStream();
          page.compress(Bitmap.CompressFormat.PNG, 100, stream);
          byte[] byteArray = stream.toByteArray();
          stream.reset();
          java.lang.String base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP);
          PDF.pdf_pages.get(i).append("<img src=\"data:image/png;base64,").append(base64).append("\"/><br/>");
          stream.close();
        }

      } catch (Exception e) {
        Log.d("error", e.toString());
      }
      return null;
    }

  }

  //Load Images:
  private void pdfLoadImages(final byte[] data) {
    try {
      // run async
      new PDFAsyncTask(PDFViewer.this, data).execute();
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

}