/*
 * FindBugs - Find bugs in Java programs
 * Copyright (C) 2003, University of Maryland
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package edu.umd.cs.findbugs;

import java.util.*;
import java.net.*;
import java.io.*;
import org.dom4j.*;
import org.dom4j.io.*;

public class PluginLoader extends URLClassLoader {

	private ArrayList<DetectorFactory> detectorFactoryList;
	private ArrayList<BugPattern> bugPatternList;
	private ArrayList<BugCode> bugCodeList;

	public PluginLoader(URL url) throws PluginException {
		super(new URL[]{url});
		init();
	}

	public DetectorFactory[] getDetectorFactoryList() {
		return null;
	}

	public BugPattern[] getBugPatternList() {
		return null;
	}

	private void init() throws PluginException {
		Document pluginDescriptor; // a.k.a, "findbugs.xml"
		Document messageCollection; // a.k.a., "messages.xml"

		try {
			URL descriptorURL = findResource("findbugs.xml");
			if (descriptorURL == null)
				throw new PluginException("Couldn't find \"findbugs.xml\" in plugin");

			SAXReader reader = new SAXReader();
			pluginDescriptor = reader.read(descriptorURL);
		} catch (DocumentException e) {
			throw new PluginException("Couldn't parse \"findbugs.xml\"", e);
		}

		try {
			URL messageURL = null;

			Locale locale = Locale.getDefault();
			String language = locale.getLanguage();
			String country = locale.getCountry();

			if (!country.equals(""))
				messageURL = findResource("messages_" + language + "_" + country + ".xml");

			if (messageURL == null)
				messageURL = findResource("messages_" + language + ".xml");

			if (messageURL == null)
				messageURL = findResource("messages.xml");

			if (messageURL == null)
				throw new PluginException("Couldn't find messages.xml");

			SAXReader reader = new SAXReader();
			messageCollection = reader.read(messageURL);
		} catch (DocumentException e) {
			throw new PluginException("Couldn't parse \"messages.xml\"", e);
		}

		// Create a DetectorFactory for all Detector nodes
		try {
			detectorFactoryList = new ArrayList<DetectorFactory>();
			List detectorNodeList = pluginDescriptor.selectNodes("/FindbugsPlugin/Detector");
			for (Iterator i = detectorNodeList.iterator(); i.hasNext(); ) {
				Node detectorNode = (Node) i.next();
				String className = detectorNode.valueOf("@class");
				String disabled = detectorNode.valueOf("@disabled");
	
				//System.out.println("Found detector: class="+className+", disabled="+disabled);
	
				if (!disabled.equals("true")) {
					Class detectorClass = loadClass(className);
					DetectorFactory factory = new DetectorFactory(detectorClass);
					detectorFactoryList.add(factory);
				}
			}
		} catch (ClassNotFoundException e) {
			throw new PluginException("Could not instantiate detector class", e);
		}

		// Create BugPatterns
		bugPatternList = new ArrayList<BugPattern>();
		List bugPatternNodeList = pluginDescriptor.selectNodes("/FindbugsPlugin/BugPattern");
		for (Iterator i = bugPatternNodeList.iterator(); i.hasNext(); ) {
			Node bugPatternNode = (Node) i.next();
			String type = bugPatternNode.valueOf("@type");
			String abbrev = bugPatternNode.valueOf("@abbrev");
			String category = bugPatternNode.valueOf("@category");

			// Find the matching element in messages.xml
			String query = "/MessageCollection/BugPattern[@type='" + type + "']";
			Node messageNode = (Node) messageCollection.selectSingleNode(query);
			if (messageNode == null)
				throw new PluginException("messages.xml missing BugPattern element for type " + type);
			String shortDesc = getChildText(messageNode, "ShortDescription");
			String longDesc = getChildText(messageNode, "LongDescription");
			String detailText = getChildText(messageNode, "Details");

			BugPattern bugPattern = new BugPattern(type, abbrev, category, shortDesc, longDesc, detailText);
			bugPatternList.add(bugPattern);
		}

		// Create BugCodes
		bugCodeList = new ArrayList<BugCode>();
		List bugCodeNodeList = messageCollection.selectNodes("/MessageCollection/BugCode");
		for (Iterator i = bugCodeNodeList.iterator(); i.hasNext(); ) {
			Node bugCodeNode = (Node) i.next();
			String abbrev = bugCodeNode.valueOf("@abbrev");
			if (abbrev.equals(""))
				throw new PluginException("BugCode element with missing abbrev attribute");
			String description = bugCodeNode.getText();
			BugCode bugCode = new BugCode(abbrev, description);
			bugCodeList.add(bugCode);
		}

	}

	private static String getChildText(Node node, String childName) throws PluginException {
		Node child = node.selectSingleNode(childName);
		if (child == null)
			throw new PluginException("Could not find child \"" + childName + "\" for node");
		return child.getText();
	}

	public static void main(String[] argv) {
		try {

			if (argv.length != 1) {
				System.out.println("Usage: " + PluginLoader.class.getName() + " <url>");
				System.exit(1);
			}

			URL url = new URL(argv[0]);
			PluginLoader loader = new PluginLoader(url);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}

// vim:ts=4
