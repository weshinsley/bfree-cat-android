package com.teapotrecords.bfreecat;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

class BFreeDB {

  String catVersion = "A0";
  private ArrayList<String> list_ids = new ArrayList<>();
  private ArrayList<String> list_names = new ArrayList<>();
  ArrayList<String> song_data = new ArrayList<>();
  HashMap<String,Integer> officeToIndex = new HashMap<>();
  final static byte TITLE = 0;
  final static byte ALT_TITLE = 1;
  final static byte OFFICE_NO = 2;
  final static byte AUTHOR = 3;
  final static byte COPDATE = 4;
  final static byte COPYRIGHT = 5;
  final static byte TEXT = 6;

  final static byte NO_LINKS = 7;

  void load(MainActivity a) {
    try {
      BufferedReader br = new BufferedReader(new FileReader(new File(a.getFilesDir(), "db.txt")));
      catVersion=br.readLine();
      song_data.clear();
      int no_songs = Integer.parseInt(br.readLine());
      for (int i=0; i<no_songs; i++) {
        String s = br.readLine();
        officeToIndex.put(s.split("\t")[OFFICE_NO], i);
        song_data.add(s);
      }
      int no_lists = Integer.parseInt(br.readLine());
      list_ids.clear();
      list_names.clear();
      for (int i=0; i<no_lists; i++) {
        list_names.add(br.readLine());
        list_ids.add(br.readLine());
      }
      br.close();
    } catch (Exception e) { e.printStackTrace(); }
  }

  void save(MainActivity a) {
    try {
      PrintWriter PW = new PrintWriter(new FileWriter(new File(a.getFilesDir(), "db.txt")));
      PW.println(catVersion);
      PW.println(song_data.size());
      for (int i=0; i<song_data.size(); i++) PW.println(song_data.get(i));
      PW.println(list_names.size());
      for (int i=0; i<list_names.size(); i++) {
        PW.println(list_names.get(i));
        PW.println(list_ids.get(i));
      }
      PW.close();
    } catch (Exception e) { e.printStackTrace(); }

  }

  private static void dealWithLinks(ArrayList<String> files, ArrayList<Byte> types, Node songnode) {
    Node links = XMLHelper.getTag(songnode, "links");
    int no_links = XMLHelper.countChildren(links, "link");
    for (int j = 0; j < no_links; j++) {
      Node link = XMLHelper.getChildNo(links, "link", j);
      String type = XMLHelper.getTagValue(link, "type");
      byte itype = 0;
      if (type.equals("Chords")) itype = 1;
      else if (type.equals("Sheet")) itype = 2;
      if (itype != 0) {
        files.add(XMLHelper.getTagValue(link, "file"));
        types.add(itype);
      }
    }
  }
  static void addDownloads(ArrayList<String> files, ArrayList<Byte> types, Element doc) {
    types.clear();
    files.clear();
    int getfile_count = XMLHelper.countChildren(doc, "getfile");
    for (int i = 0; i < getfile_count; i++) {
      Node gfnode = XMLHelper.getChildNo(doc, "getfile", i);
      String type = XMLHelper.getTagValue(gfnode, "type");
      byte itype = 0;
      if (type.equals("Chords")) itype = 1;
      else if (type.equals("Sheet")) itype = 2;
      if (itype != 0) {
        files.add(XMLHelper.getTagValue(gfnode, "file"));
        types.add(itype);
      }
    }
    int addsong_count = XMLHelper.countChildren(doc, "addsong");
    for (int i = 0; i < addsong_count; i++) {
      Node songnode = XMLHelper.getTag(XMLHelper.getChildNo(doc, "addsong", i), "song");
      dealWithLinks(files, types, songnode);
    }
    addsong_count = XMLHelper.countChildren(doc, "updaterecord");
    for (int i = 0; i < addsong_count; i++) {
      Node songnode = XMLHelper.getTag(XMLHelper.getChildNo(doc, "updaterecord", i), "song");
      dealWithLinks(files, types, songnode);
    }
  }

  void addSongToDB(Node addsong) {
    StringBuilder newdata = new StringBuilder();
    Node song = XMLHelper.getTag(addsong,"song");
    String new_title = XMLHelper.getTagValue(song, "title");
    String new_title_cludged=new_title.toUpperCase();
    new_title_cludged=new_title_cludged.replaceAll("[^A-Z]", "");

    newdata.append(new_title).append("\t");
    newdata.append(XMLHelper.getTagValue(song, "alttitle")).append("\t");
    newdata.append(XMLHelper.getTagValue(song, "officeno")).append("\t");
    newdata.append(XMLHelper.getTagValue(song, "author")).append("\t");
    newdata.append(XMLHelper.getTagValue(song, "copdate")).append("\t");
    newdata.append(XMLHelper.getTagValue(song, "copyright")).append("\t");
    newdata.append(XMLHelper.getTagValue(song, "text")).append("\t");

    Node links_to_add = XMLHelper.getTag(song, "links");
    int no_links = XMLHelper.countChildren(links_to_add, "link");

    newdata.append(no_links).append("\t");
    for (int i = 0; i < no_links; i++) {
      Node link_to_add = XMLHelper.getChildNo(links_to_add, "link", i);
      newdata.append(XMLHelper.getTagValue(link_to_add, "type")).append("\t");
      newdata.append(XMLHelper.getTagValue(link_to_add, "file")).append("\t");
    }
    boolean found=false;
    for (int i=0; i<song_data.size(); i++) {
      String compareAuthorCludge = song_data.get(i).split("\t")[0].toUpperCase();
      compareAuthorCludge = compareAuthorCludge.replaceAll("[^A-Z]", "");

      if (compareAuthorCludge.compareTo(new_title_cludged)>0) {
        song_data.add(i,newdata.toString());
        found=true;
        i=song_data.size();
      }
    }
    if (!found) song_data.add(newdata.toString());
  }

  void removeSong(Node removesong) {
    //  <removesong><id>1079273</id></removesong>
    String id = XMLHelper.getTagValue(removesong, "id");
    for (int i=0; i<song_data.size(); i++) {
      if (song_data.get(i).split("\t")[OFFICE_NO].equals(id)) {
        song_data.remove(i);
        i=song_data.size();
      }
    }
  }

  void updateSong(Node update) {
    // Recreate all data - remove/add with officeno.
    removeSong(update);
    addSongToDB(update);
  }



  void renameID(Node renameid) {
    // <renameid><from>4222082 </from><to>4222082</to></renameid>

    // First find the actual record.
    String fromid = XMLHelper.getTagValue(renameid, "from");
    String toid = XMLHelper.getTagValue(renameid, "to");
    for (int i=0; i<song_data.size(); i++) {
      String[] s = song_data.get(i).split("\t");
      if (s[OFFICE_NO].equals(fromid)) {
        s[OFFICE_NO]=toid;
        StringBuilder ss = new StringBuilder();
        for (String sj : s) ss.append(sj).append("\t");
        song_data.set(i,ss.toString());
        i=song_data.size();
      }
    }

    // And look in the list ids too.

    for (int i=0; i<list_ids.size(); i++) {
      String[] ss = list_ids.get(i).split("\t");
      boolean change=false;
      for (int j=0; j<ss.length; j++) {
        if (ss[j].equals(fromid)) {
          ss[j]=toid;
          change=true;
        }
      }
      if (change) {
        StringBuilder sss = new StringBuilder();
        for (String sj : ss) sss.append(sj).append("\t");
        list_ids.set(i,sss.toString());
      }
    }
  }

  void addLink(Node link) {
    // <addlink><id>12345</id><link><type><Chords></type><file>Blah</file></addlink>
    String id = XMLHelper.getTagValue(link,"id");
    for (int i=0; i<song_data.size(); i++) {
      String[] ss = song_data.get(i).split("\t");
      if (ss[OFFICE_NO].equals(id)) {
        int no_links = Integer.parseInt(ss[NO_LINKS]);
        ss[NO_LINKS]=String.valueOf(no_links + 1);
        StringBuilder sss = new StringBuilder();
        for (String sj : ss) sss.append(sj).append("\t");
        sss.append(XMLHelper.getTagValue(link,"type")).append("\t");
        sss.append(XMLHelper.getTagValue(link, "file")).append("\t");
        song_data.set(i, sss.toString());
        i=song_data.size();
      }
    }
  }

  void removeLink(Node link) {
    // Not used. But:   <removelink><id>12345</id><type>Chords</type><file>Blah</file></removelink>
    String id = XMLHelper.getTagValue(link,"id");
    String type = XMLHelper.getTagValue(link, "type");
    String file = XMLHelper.getTagValue(link, "file");
    for (int i=0; i<song_data.size(); i++) {
      String[] ss = song_data.get(i).split("\t");
      if (ss[OFFICE_NO].equals(id)) {
        int no_links = Integer.parseInt(ss[NO_LINKS]);
        for (int j = 0; j < no_links; j++) {
          if ((ss[NO_LINKS + 1 + (2 * j)].equals(type)) && (ss[NO_LINKS + 2 + (2 * j)].equals(file))) {
            ss[NO_LINKS + 1 + (2 * j)] = null;
            ss[NO_LINKS + 2 + (2 * j)] = null;
            ss[NO_LINKS] = String.valueOf(no_links - 1);
            StringBuilder sss = new StringBuilder();
            for (String sk : ss) {
              if (sk != null) sss.append(sk).append("\t");
            }
            song_data.set(i, sss.toString());
            j = no_links;
            i = song_data.size();
          }
        }
      }
    }
  }

  //-------- List Support -----------//

  void createList(Node createlist) {
    //   <createlist>Popular Songs</createlist>

    String newlist = createlist.getTextContent();
    list_names.add(newlist);
    list_ids.add("");
  }

  void removeList(Node createlist) {
    // <removelist>Update 2004</removelist>
    String dellist = createlist.getTextContent();
    for (int i=0; i<list_names.size(); i++) {
      if (list_names.get(i).equals(dellist)) {
        list_names.remove(i);
        list_ids.remove(i);
        i=list_names.size();
      }
    }
  }

  void renameList(Node createlist) {
    // Not used in any updates...
    String fromlist = XMLHelper.getTagValue(createlist,"from");
    String tolist = XMLHelper.getTagValue(createlist, "to");
    for (int i=0; i<list_names.size(); i++) {
      if (list_names.get(i).equals(fromlist)) {
        list_names.set(i,tolist);
        i=list_names.size();
      }
    }
  }

  void addSongToList(Node addtolist) {
    //<addsongtolist><id>18723</id><list>Popular Songs</list></addsongtolist>
    // Looks odd = recheck if we implement lists on android.
    //String id = XMLHelper.getTagValue(addtolist, "id");
    String list = XMLHelper.getTagValue(addtolist, "list");
    for (int i=0; i<list_names.size(); i++) {
      if (list_names.get(i).equals(list)) {
        String s = list_ids.get(i)+"\t";
        list_ids.set(i,s);
        i=list_names.size();
      }
    }
  }

  void removeSongFromList(Node addtolist) {
    //<addsongtolist><id>18723</id><list>Popular Songs</list></addsongtolist>
    String id = XMLHelper.getTagValue(addtolist, "id");
    String list = XMLHelper.getTagValue(addtolist, "list");
    for (int i = 0; i < list_names.size(); i++) {
      if (list_names.get(i).equals(list)) {
        String[] s = list_ids.get(i).split("\t");
        for (int j = 0; j < s.length; j++) {
          if (s[j].equals(id)) {
            s[j] = null;
            j = s.length;
          }
        }
        StringBuilder sss = new StringBuilder();
        for (String sj : s) if (sj != null) sss.append(sj).append("\t");
        list_ids.set(i, sss.toString());
        i = list_names.size();
      }
    }
  }
}
