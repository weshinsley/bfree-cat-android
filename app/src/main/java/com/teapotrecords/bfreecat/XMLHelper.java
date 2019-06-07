package com.teapotrecords.bfreecat;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class XMLHelper {
  
  static Node getTag(Node root, String name) {
    NodeList nl = root.getChildNodes();
    Node result = null;
    for (int i=0; i<nl.getLength(); i++) {
      if (nl.item(i).getNodeName().equals(name)) {
        result = nl.item(i);
        i=nl.getLength();
      }
    }
    return result;
  }

  
  static String getTagValue(Node root, String name) {
    Node tag = getTag(root,name);
   return tag.getTextContent();
  }
  
 static int countChildren(Node parent) {
    int i=0;
    for (int j=0; j<parent.getChildNodes().getLength(); j++) {
      if (parent.getChildNodes().item(j).getNodeType()==Node.ELEMENT_NODE) i++;
    }
    return i;
  }
  
  static int countChildren(Node parent,String tag) {
    int i=0;
    for (int j=0; j<parent.getChildNodes().getLength(); j++) {
      if (parent.getChildNodes().item(j).getNodeType()==Node.ELEMENT_NODE) {
        if (parent.getChildNodes().item(j).getNodeName().equals(tag)) i++;
      }
    }
    return i;
  }
  
  static Node getChildNo(Node parent,String tag,int n) {
    int i=0;
    Node result=null;
    for (int j=0; j<parent.getChildNodes().getLength(); j++) {
      if (parent.getChildNodes().item(j).getNodeType()==Node.ELEMENT_NODE) {
        if (parent.getChildNodes().item(j).getNodeName().equals(tag)) {
          if (i==n) {
            result = parent.getChildNodes().item(j);
            j=parent.getChildNodes().getLength();
          }
          i++;
        }
      }
    }
    return result;
  }
  
  static Node getChildNo(Node parent,int n) {
    int i=0;
    Node result=null;
    for (int j=0; j<parent.getChildNodes().getLength(); j++) {
      if (parent.getChildNodes().item(j).getNodeType()==Node.ELEMENT_NODE) {
        if (i==n) {
          result = parent.getChildNodes().item(j);
          j=parent.getChildNodes().getLength();
        }
        i++;
      }
    }
    return result;
  }
}
