/*
 * This file is part of the DITA Open Toolkit project hosted on
 * Sourceforge.net. See the accompanying license.txt file for 
 * applicable licenses.
 */

/*
 * (c) Copyright IBM Corp. 2007 All Rights Reserved.
 */
package org.dita.dost.reader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Random;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.dita.dost.log.DITAOTJavaLogger;
import org.dita.dost.log.MessageUtils;
import org.dita.dost.module.Content;
import org.dita.dost.module.ContentImpl;
import org.dita.dost.util.Constants;
import org.dita.dost.util.FileUtils;
import org.dita.dost.util.StringUtils;
import org.dita.dost.writer.ChunkTopicParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;


public class ChunkMapReader implements AbstractReader {
	
	private DITAOTJavaLogger javaLogger = null;
	
	private String mapChunk = null;
	
	private String filePath = null;
	
	private Hashtable changeTable = null;
	
	private HashSet refFileSet = null;
	
	private String ditaext = null;
	
	private String transtype = null;

	public ChunkMapReader() {
		super();
		javaLogger = new DITAOTJavaLogger();
		mapChunk="by-document";
		changeTable = new Hashtable(Constants.INT_128);
		refFileSet = new HashSet(Constants.INT_128);
		
	}

	public void read(String filename) {
		File inputFile = new File(filename);
        filePath = inputFile.getParent();
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.parse(filename);
			
			Element root = doc.getDocumentElement();
			NodeList list = root.getChildNodes();
			
			if("by-topic".equals(root.getAttribute(Constants.ATTRIBUTE_NAME_CHUNK))){
				mapChunk="by-topic";
			}else{
				mapChunk="by-document";
			}
			
			
			for (int i = 0; i < list.getLength(); i++){
				Node node = list.item(i);
				Node classAttr = null;
				String classValue = null;
				if (node.getNodeType() == Node.ELEMENT_NODE){
					classAttr = node.getAttributes().getNamedItem("class");
					
					if(classAttr != null){
						classValue = classAttr.getNodeValue();
					}
					
					if(classValue != null && classValue.indexOf(Constants.ATTR_CLASS_VALUE_RELTABLE)!=-1){
						updateReltable((Element)node);
					}
					if(classValue != null && classValue.indexOf(Constants.ATTR_CLASS_VALUE_TOPICREF)!=-1){
						processTopicref(node);
					}
					
				}
			}
			
			outputMapFile(inputFile.getAbsolutePath()+".chunk",root);
			if(!inputFile.delete()){
            	Properties prop = new Properties();
            	prop.put("%1", inputFile.getPath());
            	prop.put("%2", inputFile.getAbsolutePath()+".chunk");
            	javaLogger.logError(MessageUtils.getMessage("DOTJ009E", prop).toString());
            }
            if(!new File(inputFile.getAbsolutePath()+".chunk").renameTo(inputFile)){
            	Properties prop = new Properties();
            	prop.put("%1", inputFile.getPath());
            	prop.put("%2", inputFile.getAbsolutePath()+".chunk");
            	javaLogger.logError(MessageUtils.getMessage("DOTJ009E", prop).toString());
            }
			
		}catch (Exception e){
			javaLogger.logException(e);
		}

	}

	private void outputMapFile(String file, Element root) {
		// TODO Auto-generated method stub
		OutputStreamWriter output = null;
		try{
		output = new OutputStreamWriter(
				new FileOutputStream(file),
				Constants.UTF8);
		output(root,output);
		output.flush();
		output.close();
		}catch (Exception e) {
			javaLogger.logException(e);
		}finally{
			try{
				if(output!=null){
					output.close();
				}
			}catch (Exception e) {
				javaLogger.logException(e);
			}
		}
	}
		
	private void output(ProcessingInstruction instruction,Writer outputWriter) throws IOException{
		outputWriter.write("<?"+instruction.getTarget()+" "+instruction.getData()+"?>");		
	}


	private void output(Text text, Writer outputWriter) throws IOException{
		outputWriter.write(StringUtils.escapeXML(text.getData()));
	}


	private void output(Element elem, Writer outputWriter) throws IOException{
		outputWriter.write("<"+elem.getNodeName());
		NamedNodeMap attrMap = elem.getAttributes();
		for (int i = 0; i<attrMap.getLength(); i++){
			outputWriter.write(" "+attrMap.item(i).getNodeName()
					+"=\""+StringUtils.escapeXML(attrMap.item(i).getNodeValue())
					+"\"");
		}
		outputWriter.write(">");
		NodeList children = elem.getChildNodes();
		Node child;
		for (int j = 0; j<children.getLength(); j++){
			child = children.item(j);
			switch (child.getNodeType()){
			case Node.TEXT_NODE:
				output((Text) child, outputWriter); break;
			case Node.PROCESSING_INSTRUCTION_NODE:
				output((ProcessingInstruction) child, outputWriter); break;
			case Node.ELEMENT_NODE:
				output((Element) child, outputWriter);
			}
		}
		
		outputWriter.write("</"+elem.getNodeName()+">");
	}
	
	private void processTopicref(Node node) {
		NamedNodeMap attr = null;
		Node hrefAttr = null;
		Node chunkAttr = null;
		Node copytoAttr = null;
		String hrefValue = null;
		String chunkValue = null;
		String copytoValue = null;
		
		attr = node.getAttributes();
		
		hrefAttr = attr.getNamedItem(Constants.ATTRIBUTE_NAME_HREF);
		chunkAttr = attr.getNamedItem(Constants.ATTRIBUTE_NAME_CHUNK);
		copytoAttr = attr.getNamedItem(Constants.ATTRIBUTE_NAME_COPY_TO);
		if(hrefAttr != null){
			hrefValue = hrefAttr.getNodeValue();
		}		
		if(chunkAttr != null){
			chunkValue = chunkAttr.getNodeValue();
		}	
		if(copytoAttr != null){
			copytoValue = copytoAttr.getNodeValue();
		}	
		
		if(chunkValue != null && 
				chunkValue.indexOf("to-content") != -1){
			//if this is the start point of the content chunk
			processChunk((Element)node,false);
		}else if(chunkValue != null &&
				chunkValue.indexOf("to-navigation")!=-1 &&
				Constants.INDEX_TYPE_ECLIPSEHELP.equals(transtype)){
			//if this is the start point of the navigation chunk
			processChildTopicref(node);
			//create new map file
			//create new map's root element			
			Node root = node.getOwnerDocument().getDocumentElement().cloneNode(false);
			//create navref element
			Element navref = node.getOwnerDocument().createElement("navref");
			Random random = new Random();
			String newMapFile = "MAPCHUNK"+Math.abs(random.nextInt())+".ditamap";
			navref.setAttribute("mapref",newMapFile);
			//replace node with navref
			node.getParentNode().replaceChild(navref,node);
			root.appendChild(node);			
			// generate new file
			outputMapFile(FileUtils.resolveFile(filePath,newMapFile),(Element)root);
			
		}else if("by-topic".equals(mapChunk)){
			processChunk((Element)node,true);
			processChildTopicref(node);
		}else{
			String currentPath = null;
			if(copytoValue != null){
				currentPath = FileUtils.resolveFile(filePath, copytoValue);
			}else if(hrefValue != null){
				currentPath = FileUtils.resolveFile(filePath, hrefValue);
			}
			if(currentPath != null){
				if(changeTable.containsKey(currentPath)){
					changeTable.remove(currentPath);				
				}
				if(!refFileSet.contains(currentPath)){
					refFileSet.add(currentPath);
				}			
			}
			
			processChildTopicref(node);
		}	
	}	
	

	private void processChildTopicref(Node node) {
		NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); i++){
			Node current = children.item(i);
			if(current.getNodeType()==Node.ELEMENT_NODE){
				String classValue  = ((Element)current).getAttribute(Constants.ATTRIBUTE_NAME_CLASS);
				String hrefValue = ((Element)current).getAttribute(Constants.ATTRIBUTE_NAME_HREF);
				if(classValue.indexOf(Constants.ATTR_CLASS_VALUE_TOPICREF)!=-1){
					if(!hrefValue.equals(Constants.STRING_EMPTY) &&
							FileUtils.resolveFile(filePath,hrefValue)
							.equals(changeTable.get(FileUtils.resolveFile(filePath,hrefValue)))){
						//make sure hrefValue make sense and target file 
						//is not generated file
						processTopicref(current);
					}					
				}
			}
		}
		
	}

	private void processChunk(Element elem, boolean separate) {
		//set up ChunkTopicParser
		try{
			ChunkTopicParser chunkParser = new ChunkTopicParser();
			chunkParser.setup(changeTable, refFileSet, elem, separate, ditaext);
			chunkParser.write(filePath);
		}catch (Exception e) {
			javaLogger.logException(e);
		}
	}

	private void updateReltable(Element elem) {
		// TODO Auto-generated method stub
		String hrefValue = elem.getAttribute(Constants.ATTRIBUTE_NAME_HREF);
		String resulthrefValue = null;
		if (!hrefValue.equals(Constants.STRING_EMPTY)){
			if(changeTable.containsKey(FileUtils.resolveFile(filePath,hrefValue))){
				if (hrefValue.indexOf(Constants.SHARP)!=-1){
					resulthrefValue=FileUtils.getRelativePathFromMap(filePath+Constants.SLASH+"stub.ditamap"
							,FileUtils.resolveFile(filePath,hrefValue))
					+ hrefValue.substring(hrefValue.indexOf(Constants.SHARP)+1);
				}else{
					resulthrefValue=FileUtils.getRelativePathFromMap(filePath+Constants.SLASH+"stub.ditamap"
							,FileUtils.resolveFile(filePath,hrefValue));
				}
				elem.setAttribute(Constants.ATTRIBUTE_NAME_HREF, resulthrefValue);
			}
		}
		NodeList children = elem.getChildNodes();
		for(int i = 0; i < children.getLength(); i++){
			Node current = children.item(i);
			if(current.getNodeType() == Node.ELEMENT_NODE){
				String classValue = ((Element)current).getAttribute(Constants.ATTRIBUTE_NAME_CLASS);
				if (classValue.indexOf(Constants.ATTR_CLASS_VALUE_TOPICREF)!=-1){
					
				}
			}
		}
	}

	public Content getContent() {
		Content content = new ContentImpl();
		content.setValue(changeTable);
		return content;
	}

	public void setup(String ditaext, String transtype) {
		this.ditaext = ditaext;
		this.transtype = transtype;
		
	}

}
