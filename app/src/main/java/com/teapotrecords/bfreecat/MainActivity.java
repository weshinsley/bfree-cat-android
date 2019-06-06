package com.teapotrecords.bfreecat;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.InputType;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends AppCompatActivity {
  static final String PREFS_NAME = "BfreeCatalogue";
  static final String rootWeb = "http://www.teapotrecords.co.uk/bfree";
  static final String versionFile = rootWeb+"/XML/version.xml";
  static final byte TITLE_AZ = 0;
  static final byte TITLE_ZA = 1;
  static final byte AUTHOR_AZ = 2;
  static final byte AUTHOR_ZA = 3;
  static final byte COPDATE_AZ = 4;
  static final byte COPDATE_ZA = 5;

  String appVersion = "0.10";

  byte current_sort = TITLE_AZ;
  byte next_sort = TITLE_AZ;

  String latestAndroidVersion = "0.9"; //
  String latestCatVersion= "A0";
  ProgressDialog progressDialog;
  BFreeDB db;
  boolean search_did_something = false;

  ArrayList<String> download_list = new ArrayList<>();
  ArrayList<Byte> download_types = new ArrayList<>();
  ArrayList<String> rowOfficeNo = new ArrayList<>();

  // Some utility bits

  private void unlockOrientation() {
    MainActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
  }

  @SuppressWarnings("deprecation")
  private void lockOrientation() {
    Display display = MainActivity.this.getWindowManager().getDefaultDisplay();
    int rotation = display.getRotation();
    int height, width;
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR2) {
      height = display.getHeight();
      width = display.getWidth();
    } else {
      Point size = new Point();
      display.getSize(size);
      height = size.y;
      width = size.x;
    }
    if (rotation == Surface.ROTATION_90) {
      if (width > height)
        MainActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
      else MainActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
    } else if (rotation == Surface.ROTATION_180) {
      if (height > width) MainActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
      else MainActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
    } else if (rotation == Surface.ROTATION_270) {
      if (width > height) MainActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
      else MainActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    } else {
      if (height > width) MainActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
      else MainActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }
  }

  protected void initialiseFiles() {
    try {
      db.save(this);
      new File(MainActivity.this.getFilesDir(),"Chords").mkdirs();
      new File(MainActivity.this.getFilesDir(),"Sheet").mkdirs();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);

    LayoutInflater inflater = (LayoutInflater) getSupportActionBar().getThemedContext().getSystemService(LAYOUT_INFLATER_SERVICE);
    View customActionBarView = inflater.inflate(R.layout.actionbar_custom, null);

    ActionBar actionBar = getSupportActionBar();
    actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM, ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
    actionBar.setCustomView(customActionBarView, new ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    actionBar.setTitle("");
    actionBar.setBackgroundDrawable(new ColorDrawable(Color.WHITE));

    ImageView iv = (ImageView) findViewById(R.id.searchButton);
    iv.setOnClickListener(searchButtonListener);

    iv = (ImageView) findViewById(R.id.resetSearch);
    iv.setOnClickListener(resetButtonListener);

    iv = (ImageView) findViewById(R.id.sortButton);
    iv.setOnClickListener(sortButtonListener);

    // Check for first ever use.
    db = new BFreeDB();
    File file = new File(MainActivity.this.getFilesDir(), "db.txt");
    if (!file.exists()) initialiseFiles();
    db.load(this);
    resetTable();
    MainActivity.this.runOnUiThread(versionFetcher);
  }

  void applySearch(String search) {
    search=search.toUpperCase();
    search=search.replaceAll("[^A-Z]","");
    final TableLayout tl = (TableLayout) findViewById(R.id.thetable);
    for (int i=rowOfficeNo.size()-1; i>=0; i--) {
      int index = db.officeToIndex.get(rowOfficeNo.get(i));
      String text = db.song_data.get(index).split("\t")[BFreeDB.TEXT];
      if (!text.contains(search)) {
        rowOfficeNo.remove(i);
        tl.removeViewAt(i);
        search_did_something=true;
      }
    }
    if (search_did_something) {
      ImageView v = (ImageView) findViewById(R.id.resetSearch);
      v.setImageResource(R.drawable.reset_search);
      boolean landscape = areWeLandscape();
      int mainlabel=0;
      for (int i=0; i<rowOfficeNo.size(); i++) {
        TableRow tablerow = (TableRow) (tl.getChildAt(i));
        tablerow.setBackgroundColor((i % 2 == 0) ? Color.rgb(255, 255, 255) : Color.rgb(240, 240, 240));
        ImageView iv = (ImageView) tablerow.getChildAt(landscape?2:1);
        iv.setId(100000+i);
        if (iv.isEnabled()) mainlabel=100000+i;
        iv = (ImageView) tablerow.getChildAt(landscape?4:3);
        iv.setId(200000+i);
        if (iv.isEnabled()) mainlabel=200000+i;
        iv = (ImageView) tablerow.getChildAt(landscape?6:5);
        iv.setId(300000+i);
        if (iv.isEnabled()) mainlabel=300000+i;
        TextView tv = (TextView) tablerow.getChildAt(0);
        tv.setId(mainlabel);
      }
    }
  }

  boolean areWeLandscape() {
    Display display = MainActivity.this.getWindowManager().getDefaultDisplay();
    int height, width;
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR2) {
      height = display.getHeight();
      width = display.getWidth();
    } else {
      Point size = new Point();
      display.getSize(size);
      height = size.y;
      width = size.x;
    }
    return (width>height);
  }

  void addToTable(String[] bits,boolean landscape, int i, TableLayout tl) {
    int no_links = Integer.parseInt(bits[BFreeDB.NO_LINKS]);
    boolean web=false;
    boolean guitar=false;
    boolean sheet=false;
    for (int j=0; j<no_links; j++) {
      String type = bits[BFreeDB.NO_LINKS+1+(j*2)];
      if (type.equals("Chords")) guitar=true;
      else if (type.equals("Sheet")) sheet=true;
      else if (type.equals("MP3")) web=true;
    }
    TableRow tr = new TableRow(this);
    tr.setLayoutParams(new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT));
    TextView labelTV = new TextView(this);
    if (landscape) labelTV.setText(Html.fromHtml("<html><body><font color=\"#000000\"<big><big><big>" + bits[BFreeDB.TITLE] + "</big></big></big></font><br/><font color=\"#808080\"><small>" + bits[BFreeDB.ALT_TITLE] + "</small></font></body></html>"));
    else {
      labelTV.setText(Html.fromHtml("<html><body><font color=\"#000000\"<big><big><big>" + bits[BFreeDB.TITLE] + "</big></big></big></font><br/><font color=\"#404040\"><small>" + bits[BFreeDB.ALT_TITLE] +
        "</small></font><br/><font color=\"#808080\"><small>"+bits[BFreeDB.AUTHOR]+"<br/>&copy; "+bits[BFreeDB.COPDATE]+" "+bits[BFreeDB.COPYRIGHT]+"</small></font></body></html>"));
    }
    tr.addView(labelTV);
    if (guitar) labelTV.setId(300000+i);
    else if (sheet) labelTV.setId(200000+i);
    else labelTV.setId(100000+i);
    labelTV.setOnClickListener(buttonListener);


    rowOfficeNo.add(bits[BFreeDB.OFFICE_NO]);

    if (landscape) {

      TextView labelTV2 = new TextView(this);
      labelTV2.setText(Html.fromHtml("<html><body><small><font color=\"#808080\">" + bits[BFreeDB.AUTHOR] + "<br/>&copy " + bits[BFreeDB.COPDATE] + " " + bits[BFreeDB.COPYRIGHT] + "</font></small></body></html>"));
      tr.addView(labelTV2);
    }


    ImageView webIcon = new ImageView(this);
    webIcon.setImageResource(web ? R.drawable.link : R.drawable._link);
    webIcon.setMinimumWidth(40);
    webIcon.setId(100000 + i);
    if (web) webIcon.setOnClickListener(buttonListener);
    tr.addView(webIcon);

    TextView spacer1 = new TextView(this);
    spacer1.setMinimumWidth(10);
    tr.addView(spacer1);


    ImageView sheetIcon = new ImageView(this);
    sheetIcon.setImageResource(sheet ? R.drawable.clef : R.drawable._clef);
    sheetIcon.setMinimumWidth(40);
    sheetIcon.setId(200000 + i);
    if (sheet) sheetIcon.setOnClickListener(buttonListener);

    tr.addView(sheetIcon);

    TextView spacer2 = new TextView(this);
    spacer2.setMinimumWidth(10);
    tr.addView(spacer2);


    ImageView guitarIcon = new ImageView(this);
    guitarIcon.setImageResource(guitar ? R.drawable.guitar : R.drawable._guitar);
    guitarIcon.setMinimumWidth(40);
    guitarIcon.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT));
    guitarIcon.setId(300000 + i);
    if (guitar) guitarIcon.setOnClickListener(buttonListener);
    tr.addView(guitarIcon);

    TextView spacer3 = new TextView(this);
    spacer3.setMinimumWidth(20);
    tr.addView(spacer3);

    tr.setPadding(10,10,10,10);

    if (i%2==0) tr.setBackgroundColor(Color.rgb(220,220,220));

    tl.addView(tr, new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT));

  }

  void resetTable() {
    rowOfficeNo.clear();
    final TableLayout tl = (TableLayout) findViewById(R.id.thetable);
    ImageView v = (ImageView) findViewById(R.id.resetSearch);
    v.setImageResource(R.drawable.reset_search_grey);
    tl.removeAllViews();
    int no_songs = db.song_data.size();
    boolean web,guitar,sheet;
    boolean landscape = areWeLandscape();
    for (int i = 0; i < no_songs; i++) {
      String[] bits = db.song_data.get(i).split("\t");
      addToTable(bits, landscape, i, tl);
    }
  }



  class NetTask extends AsyncTask<String, Void, String> {
    final byte task_type;
    static final byte CAT_VERSION = 1;
    static final byte DO_UPDATE = 2;

    public NetTask(byte type) {
      super();
      task_type=type;
    }

    private void downloadFile(URL url, File out) {
      try {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("GET");
        c.setDoOutput(true);
        c.connect();
        FileOutputStream f = new FileOutputStream(out);
        InputStream in = c.getInputStream();

        byte[] buffer = new byte[1024];
        int len1;// = 0;
        while ((len1 = in.read(buffer)) > 0) {
          f.write(buffer,0,len1);
        }
        f.close();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    private boolean pingTest() {
      Runtime runtime = Runtime.getRuntime();
      boolean ok=true;
      try {
        Process mIpAddrProcess = runtime.exec("/system/bin/ping -w 2 -c 1 8.8.8.8");
        int mExitValue = mIpAddrProcess.waitFor();
        ok=(mExitValue==0);
      } catch (Exception e) { ok=false; e.printStackTrace(); }
      return ok;
    }

    void checkCatalogueVersion(URL url) {
      if (pingTest()) {
        Document doc = XMLFromStream(url);
        if (doc != null) {
          Element root = doc.getDocumentElement();
          latestCatVersion = XMLHelper.getTagValue(root, "latesta");
          latestAndroidVersion = XMLHelper.getTagValue(root, "android");
          if (!latestAndroidVersion.equals(appVersion)) {
            MainActivity.this.runOnUiThread(offerSoftwareUpdate);
          } else if (!latestCatVersion.equals(db.catVersion)) {
            MainActivity.this.runOnUiThread(offerUpdate);
          }
        }
      }
    }

    void startUpdate() {
      while (!db.catVersion.equals(latestCatVersion)) {
        URL url=null;
        try {
          url = new URL(rootWeb + "/XML/up." + db.catVersion + ".xml");
        } catch (Exception e) { e.printStackTrace(); }
        MainActivity.this.setProgress("Updating", "Downloading Definition");
        Element doc = XMLFromStream(url).getDocumentElement();
        String oldversion = XMLHelper.getTagValue(doc, "fromid");
        String newversion = XMLHelper.getTagValue(doc, "toid");
        MainActivity.this.setProgress("Updating from " + oldversion + " to " + newversion, null);
        BFreeDB.addDownloads(download_list, download_types, doc);

        for (int i = 0; i < download_list.size(); i++) {
          MainActivity.this.setProgress(null, "Downloading " + download_list.get(i));
          String dir;
          if (download_types.get(i) == 1) dir = "Chords";
          else if (download_types.get(i) == 2) dir = "Sheet";
          else dir = "";
          try {
            downloadFile(new URL(rootWeb + "/Files/" + download_list.get(i)), new File(MainActivity.this.getFilesDir() + "/" + dir, download_list.get(i)));
          } catch (Exception e) {
            e.printStackTrace();
          }
        }

        MainActivity.this.setProgress(null, "Updating database");
        int no_tags = XMLHelper.countChildren(doc);
        for (int i=0; i<no_tags; i++) {
          Node tag = XMLHelper.getChildNo(doc,i);
          String cmd = tag.getNodeName();
          if (cmd.equals("addsong")) db.addSongToDB(tag);
          else if (cmd.equals("removesong")) db.removeSong(tag);
          else if (cmd.equals("updaterecord")) db.updateSong(tag);
          else if (cmd.equals("renameid")) db.renameID(tag);
          else if (cmd.equals("addlink")) db.addLink(tag);
          else if (cmd.equals("removelink")) db.removeLink(tag);
          else if (cmd.equals("createlist")) db.createList(tag);
          else if (cmd.equals("renamelist")) db.renameList(tag);
          else if (cmd.equals("removelist")) db.removeList(tag);
          else if (cmd.equals("addsongtolist")) db.addSongToList(tag);
          else if (cmd.equals("removesongfromlist")) db.removeSongFromList(tag);
          else if (cmd.equals("verify")) { } // Do nothing with verify. Do it all at the end.
          else if ((cmd.equals("fromid")) || (cmd.equals("toid")) || (cmd.equals("getfile"))) { } // Do nothing
          else System.out.println("UNRECOGNISED UPDATE COMMAND: "+cmd);
        }

        db.catVersion = newversion;
        db.save(MainActivity.this);
      }
      MainActivity.this.setProgress(null, null);
      db.load(MainActivity.this);
      unlockOrientation();
      MainActivity.this.runOnUiThread(tableReset);
    }

    private Document XMLFromStream(URL url) {
      Document doc;
      try {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        URLConnection urlconn = url.openConnection();
        urlconn.setConnectTimeout(1000);
        doc = builder.parse(urlconn.getInputStream());
      } catch (Exception e) {
        //e.printStackTrace();
        doc=null;
      }
      return doc;
    }
    @Override
    protected String doInBackground(String... params) {
      try {
        if (task_type == CAT_VERSION) checkCatalogueVersion(new URL(params[0]));
        else if (task_type == DO_UPDATE) startUpdate();

      }
      catch (Exception e) {
        //e.printStackTrace();
        //MainActivity.this.runOnUiThread(netError);
        //error_flag=true;
        return null;
      }
      return null;
    }
  }

  private final Runnable versionFetcher = new Runnable() {
    public void run() {
      if (progressDialog==null) progressDialog=new ProgressDialog(MainActivity.this);
      new NetTask(NetTask.CAT_VERSION).execute(versionFile);
    }
  };


  private final Runnable tableReset = new Runnable() {
    public void run() {
      resetTable();
    }
  };

  private final Runnable offerSoftwareUpdate = new Runnable() {
    public void run() {
      AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
      alert.setTitle("New Software!"); //Set Alert dialog title here
      alert.setMessage("I need to update myself from version  " + appVersion + " to " + latestAndroidVersion); //Message here
      alert.setPositiveButton("Alright then", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
          String ff = "http://www.teapotrecords.co.uk/bfree/bfree.apk";
          startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(ff)));
        }
      });
      alert.setNegativeButton("Too Busy Now", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
          dialog.cancel();
        }
      }); //End of alert.setNegativeButton
      alert.setCancelable(false);
      AlertDialog alertDialog = alert.create();
      alertDialog.show();
    }
  };

  private final Runnable offerUpdate = new Runnable() {
    public void run() {
       AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
        alert.setTitle("New Songs!"); //Set Alert dialog title here
        alert.setMessage("Do you want to update from catalogue " + db.catVersion + " to " + latestCatVersion); //Message here
        alert.setPositiveButton("Alright then", new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int whichButton) {
          lockOrientation();
          progressDialog.setTitle("Contemplating");
          progressDialog.setMessage("Preparing...");
          progressDialog.setCancelable(false);
          progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
          progressDialog.show();
          new NetTask(NetTask.DO_UPDATE).execute();
          dialog.cancel();
        }
      }); //End of alert.setNegativeButton
      alert.setNegativeButton("Maybe Later", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
          dialog.cancel();
        }
      }); //End of alert.setNegativeButton
      alert.setCancelable(false);
      AlertDialog alertDialog = alert.create();
      alertDialog.show();
    }
  };

  void setProgress(String t, String m) {
    final String tt = t;
    final String mm = m;
    MainActivity.this.runOnUiThread(new Runnable() {
      public void run() {
        if ((mm == null) && (tt == null)) {
          progressDialog.cancel();
        } else {
          if (mm != null) progressDialog.setMessage(mm);
          if (tt != null) progressDialog.setTitle(tt);
        }
      }
    });
  }

  private final Runnable searchResetButton = new Runnable() {
    public void run() {
      ImageView iv = (ImageView) MainActivity.this.findViewById(R.id.resetSearch);
      iv.setImageResource(R.drawable.reset_search_grey);
      resetTable();
    }
  };

  private final View.OnClickListener resetButtonListener = new View.OnClickListener() {
    public void onClick(View v) {
      if (MainActivity.this.search_did_something) {
        search_did_something=false;
        MainActivity.this.runOnUiThread(searchResetButton);
      }
    }
  };

  private final Runnable pushSearchButton = new Runnable() {
    public void run() {
      AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
      alert.setTitle("Search Song Text"); //Set Alert dialog title here
      alert.setMessage("Search for: "); //Message here
      final EditText input = new EditText(MainActivity.this);
      input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
      input.setText("");
      alert.setView(input);
      alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
          final String searchText = input.getEditableText().toString();
          MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
              applySearch(searchText);
            }
          });

        } // End of onClick(DialogInterface dialog, int whichButton)
      }); //End of alert.setPositiveButton

      alert.setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
          dialog.cancel();
        }
      }); //End of alert.setNegativeButton
      AlertDialog alertDialog = alert.create();
      alertDialog.show();
    }
  };

  private final View.OnClickListener searchButtonListener = new View.OnClickListener() {
    public void onClick(View v) {
      MainActivity.this.runOnUiThread(pushSearchButton);
    }
  };

  private final View.OnClickListener buttonListener = new View.OnClickListener() {
    public void onClick(View v) {

      int id = v.getId();
      int mode=(id/100000)-1;
      String[] types = new String[] {"MP3","Sheet","Chords"};
      id=id%100000;

      String[] bits = db.song_data.get(db.officeToIndex.get(rowOfficeNo.get(id))).split("\t");
      int no_links = Integer.parseInt(bits[BFreeDB.NO_LINKS]);
      for (int j=0; j<no_links; j++) {
        if (bits[BFreeDB.NO_LINKS + 1 + (j * 2)].equals(types[mode])) {
          String ff = bits[BFreeDB.NO_LINKS+2+(j*2)];
          if (mode==0) {
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(ff)));
          } else {
            Intent goPDF = new Intent(MainActivity.this, PDFViewer.class);
            Bundle b = new Bundle();
            b.putString("root", MainActivity.this.getFilesDir().getAbsolutePath());
            b.putString("pdffile", types[mode] + "/" + ff); //Your id
            b.putString("officeno",rowOfficeNo.get(id));
            b.putString("filetype","_"+types[mode]);
            goPDF.putExtras(b); //Put your id to your next Intent
            startActivity(goPDF);
          }
          j = no_links;
        }
      }
    }
  };


  final Runnable sortButtonPressed = new Runnable() {
    public void run() {
      final CharSequence[] items = {"Title A->Z","Title Z->A","Author A->Z","Author Z->A","Date Oldest First","Date Youngest First"};
      AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
      builder.setTitle("Sort by:");
      builder.setSingleChoiceItems(items, MainActivity.this.current_sort, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int item) {
          MainActivity.this.next_sort = (byte) item;
        }
      });
      builder.setPositiveButton("Sort it", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
          MainActivity.this.runOnUiThread(performSort);
        }
      });
      builder.setNegativeButton("Leave it", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
          MainActivity.this.next_sort = MainActivity.this.current_sort;
          dialog.cancel();

        }
      }); //End of alert.setNegativeButton
      AlertDialog alertDialog = builder.create();
      alertDialog.show();

    }
  };

  final View.OnClickListener sortButtonListener = new View.OnClickListener() {
    public void onClick(View v) {
      MainActivity.this.runOnUiThread(sortButtonPressed);

    }
  };

  final Runnable performSort = new Runnable() {
    public void run() {
      String[] sort_me = new String[rowOfficeNo.size()];
      for (int i=0; i<rowOfficeNo.size(); i++) {
        String[] s = db.song_data.get(db.officeToIndex.get(rowOfficeNo.get(i))).split("\t");
        String title;
        if ((next_sort==TITLE_AZ) || (next_sort==TITLE_ZA)) {
          title = s[BFreeDB.TITLE] + s[BFreeDB.ALT_TITLE];
          title = title.toUpperCase();
          title = title.replaceAll("[^A-Z]", "");
          title += "\t" + rowOfficeNo.get(i);
        } else if ((next_sort==AUTHOR_AZ) || (next_sort==AUTHOR_ZA)) {
          title = s[BFreeDB.AUTHOR];
          title = title.toUpperCase();
          title = title.replaceAll("[^A-Z]", "");
          title += "\t" + rowOfficeNo.get(i);
        } else { // Must be date
          title = s[BFreeDB.COPDATE];
          title = title.toUpperCase();
          title += "\t" + rowOfficeNo.get(i);
        }
        sort_me[i]=title;
      }
      rowOfficeNo.clear();
      Arrays.sort(sort_me);
      TableLayout tl = (TableLayout) MainActivity.this.findViewById(R.id.thetable);
      tl.removeAllViews();
      boolean landscape = MainActivity.this.areWeLandscape();
      if ((next_sort==TITLE_AZ) || (next_sort==AUTHOR_AZ) || (next_sort==COPDATE_AZ)) {
        for (int i=0; i<sort_me.length; i++) {
          MainActivity.this.addToTable(db.song_data.get(db.officeToIndex.get(sort_me[i].split("\t")[1])).split("\t"),landscape,i,tl);
        }

      } else {
        int j=0;
        for (int i=sort_me.length-1; i>=0; i--) {
          MainActivity.this.addToTable(db.song_data.get(db.officeToIndex.get(sort_me[i].split("\t")[1])).split("\t"),landscape,j,tl);
          j++;
        }
      }
      current_sort=next_sort;
    }
  };
}
