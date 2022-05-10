import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Vector;
import java.util.List;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.AbstractMap.SimpleEntry;
import java.io.FileOutputStream;
import java.io.IOException;

// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.

// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.

// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

// Dr.scient. Frank Thomas Tveter, 2016

/**
 * The purpose of the RegexNode class is to simplify complicated regex matching and 
 * processing. The idea is to allow the user to "hide" parts of the text from the regex 
 * matching, or just do regex matching on the "hidden" parts of the text, or "hidden" parts 
 * of the "hidden" text etc. The "hidden" parts of the text can be recovered later.
 *
 * <p> The text can be searched ({@link #seek}) using normal regex syntax. Matches may be
 * processed and the original text replaced ({@link #replace}) following the same syntax.
 *
 * <p> If a match, or group within a match, is "hidden" by for instance {@link #hide}, it 
 * may be replaced by a label which can be empty. The label is available for later matching.
 * The "hidden" text is assigned a name ("node") so that it can be "unhidden" later for instance
 * by {@link #unhide}. When text is "unhidden" it replaces the corresponding label again. 
 * 
 * <p> If a match is replaced and "hidden", the existing nodes within the match are moved to
 * the new node. Nodes can in this way be organised in a tree structure. Nodes and their 
 * child-nodes can be processed seperately from the rest of the node tree. The node tree 
 * structure may be navigated by looping over nodes with a given name using {@link #getNode}.
 * The order in which the nodes are put into the tree follows the order they appear in the text.
 * Existing nodes must be deleted explicitly using {@link #rmnode} or recovered using for 
 * instance {@link #uhideAll}. Only "hiding" parts of an existing label causes an error. 
 *
 * <p> The "ignore"-feature can be used to make only certain nodes visible to the processing.
 * "Ignorering" is in other words a method to deactivate pattern matching for parts of the 
 * node tree, for instance if a more general pattern may match already nodeged patterns that 
 * should not be processed by {@link #hideAll}. Ignored nodes are not processed by 
 * {@link #hideAll}, {@link #replaceAll} and {@link #unhideAll}. The "ignore" status of 
 * a node can be retrieved by {@link #getIgnore}. New nodes are be default "unignored".
 * Text that has not been "hidden" yet containing nodes that are ignored can be "hidden" 
 * using {@link #hideTheRest}.  Specific nodes can be "ignored" by {@link #ignore}. 
 * 
 * <p> Anchors are non-ascii characters that are defined and used as labels to
 * make the labels unique. 
 *
 * <p> The node tree structure can be displayed using {@link #toString}.
 *
 * <p> Example:
 *     RegexNode regex=new RegexNode("The #hooded boys# boys in the hood.");
 *     System.out.format("Start text: %s\n", regex.getText());
 *     RegexNode.define("<plain>","[^!\\n#]");
 *     regex.hideAll("comment", "(?m)(?i)#(<plain>*)#","");  // hide comments
 *     regex.hideNodeGroup("text", "plain text label", 1, "comment");
 *     regex.ignoreAll("comment");
 *     System.out.format("Node tree: %s\n", regex.toString());
 *     System.out.format("Work text: %s\n", regex.getText());
 *     regex.replaceAll("boys","girls");
 *     regex.replaceAll("hood","wood");
 *     regex.unignoreAll(); 
 *     regex.unhideAll();
 *     System.out.format("Final text: %s\n", regex.getText());
 *
 * <p> This yields:
 *      Start text: The #hooded boys# boys in the hood.
 *      Node tree: 
 *      0## "The ][ boys in the hood." Root [0] g=1 "hooded boys"(5,16)  
 *               11                  
 *        1#comment# "#[plain text label]#" => 0#(4 4) #i <-99 1 -99> g=1 "hooded boys"(1,17)         
 *                     2                2 
 *          2#text# "hooded boys" => 1#(1 17) <-99 2 -99> no groups      
 *                              
 *      Work text: The  boys in the hood.
 *      Final text: The #hooded boys# girls in the wood.
 *
 *
 * @author      Dr.scient. Frank Thomas Tveter
 */

public final class RegexNode {

    public static final int noSubLevels = 0;
    public static final int allSubLevels = -1;
    /**
     * original text
     */
    private StringBuffer originalText;
    /**
     * Result of all replacements so far
     */
    private StringBuilder resultText;

    /**
     * Matcher object currently used
     */
    private Matcher matcher;
    /**
     * Pattern object
     */
    private Pattern pattern;
    /**
     * resultText start index for current match
     */
    private HashMap<Integer,Integer> matchStartIndexShifted=new HashMap<Integer,Integer>();
    private HashMap<Integer,Integer> matchStartIndexOriginal=new HashMap<Integer,Integer>();
    /**
     * resultText end index for current match
     */
    private HashMap<Integer,Integer> matchEndIndexShifted=new HashMap<Integer,Integer>();
    private HashMap<Integer,Integer> matchEndIndexOriginal=new HashMap<Integer,Integer>();
    /**
     * Number of groups for current match
     */
    private int nMatchGroups=-1;
    /**
     * groups for current match
     */
    private HashMap<Integer,String> matchGroups=new HashMap<Integer,String>();
    /**
     * offset between originalText and resultText for current match
     */
    private int matchOffset;
    /**
     * position of current match in resultText
     */
    private int matchPos;
    /**
     * Has the resultText within the match changed?
     */
    private boolean matchResultChanged;
    /**
     * regex used in previous call to "seek"
     */
    private StringBuffer oldPatternString=null;
    /**
     * The RegexNode node has a linked list of children. 
     * This is coded here to allow full control so that we may 
     * loop through and modify the chain simultaneously.
     *
     *  "prevSibling" points to the previous sibling node.
     */
    private RegexNode prevSibling;
    /**
     * "nextSibling" points to the next sibling node
     */
    private RegexNode nextSibling;
    /**
     * "parentNode" points to parent node (or NULL for the top node)
     */
    private RegexNode parentNode;
    /**
     * "firstChild" points to the first child node (virtual node)
     */
    private RegexNode firstChild;
    /**
     * "lastChild" points to the last child node (virtual node)
     */
    private RegexNode lastChild;
    /**
     * "startFoldNode/endFoldNode" is used to unfold and fold nodes
     */
    private RegexNode startFoldNode;
    private RegexNode endFoldNode;
    /**
     * "parentNodeStartIndex" points to start position in parent resultText
     */
    private int parentNodeStartIndex;
    /**
     * "parentNodeEndIndex" points to end position in parent resultText (1 past last character)
     */
    private int parentNodeEndIndex;
    /**
     * Location of the node in the node tree.
     * First element is the node count in the parent node.
     * Next is the parents node count in its parent node etc.
     * If only one node is present, the node count is zero.
     */
    public  Integer[] location;
    public  Integer   locationLevel=0;
    public  Integer   locationPosition=0;
    public  Integer   children=0;
    /**
     * Should this node/node remain hidden? Ignored nodes are not unhidden etc.
     */
    private boolean ignored=false;
    private boolean marked=false;
    private boolean unfold=false;
    /**
     * Default arguments
     **/
    private String  _node="undefined"; 
    private String  _label="undefined"; 
    private String  _tag = "?"; 
    private String  _pattern=".*";
    private String  _replacement="$0";
    private String  _att="attribute";
    private String[]  _path=new String[0]; 
    private int  _group=0;
    // <tag>;label:string;
    private Character _o=':'; // assign
    private Character _d=';'; // delimiter
    private Character _a='¤'; // tag
    private Character _t=null; // node
    private Character _s= '¤'; 
    /**
     * The "node" name for this node.
     */
    private String node="";
    //
    private int filecnt=0;
    //
    /**
     * Identification counter.
     */
    private static int maxidentification=-1;
    /**
     * getNode-search counter.
     */
    private static int maxsid=0;
    /**
     * The "identification" for this node (only used for debugging).
     */
    private int identification;
    /**
     * anchor names and their anchors.
     */
    private static LinkedHashMap<String,String> anchorMap=new LinkedHashMap<String,String>();
    private static LinkedHashMap<String,Integer> anchorCnt=new LinkedHashMap<String,Integer>();
    /**
     * number of anchors defined so far.
     */
    private static int nrAnchor=0;
    /**
     * Map to keep track of several simultaneous node-searches (depends on node)
     */
    private HashMap<Integer,RegexNode> nodeChildMap=new HashMap<Integer,RegexNode>();
    private HashMap<String,Integer> nodeSidMap=new HashMap<String,Integer>();
    private HashMap<String,Pattern> nodePatternMap=new HashMap<String,Pattern>();
    /**
     * Current child-node being node-searched
     */
    private RegexNode nodeChild;
    /**
     * node from last seek
     */
    private RegexNode lastSeek;
    /**
     * Map to keep track of attributes.
     */
    private LinkedHashMap<String,Object> attributes=new LinkedHashMap<String,Object>();
    /**
     * Types based on start/end index
     */
    private static final int nolimit=-3;
    private static final int before=-2;
    private static final int atStart=-1;
    private static final int between=0;
    private static final int atEnd=+1;
    private static final int after=+2;
    private static final int atBoth=+3;

    static private boolean debug=false;
    //
    //******************** C O N S T R U C T O R S ******************
    //
    /**
     * Constructor. 
      * @param  OriginalText
      *         The text that we want to search and process.
      */
    public RegexNode(String originalText) {
	this.parentNode=null; // this is the top node
	init(originalText);
    }
    public RegexNode(String originalText, String nodeName) {
	this.parentNode=null; // this is the top node
	init(originalText);
	setNodeName(nodeName);
    }
    public RegexNode(String originalText, Character o, Character d) {
	this.mark(o,d);
	Character a=null;
	Character t=null;
	this.parentNode=null; // this is the top node
	init(originalText);
	decode_(getText(),-1,o,d,a,-1,t);
	this.mark(o,d,a);
    }
    public RegexNode(String originalText, Character o, Character d, Character a) {
	this.mark(o,d,a);
	Character t=null;
	this.parentNode=null; // this is the top node
	init(originalText);
	decode_(getText(),-1,o,d,a,-1,t);
    }
    public RegexNode(String originalText, Character o, Character d, Character t, RegexNode... nodes) {
	Character a=null;
	this.assignMark(o);
	this.delimiterMark(d);
	this.nodeMark(t);
	this.parentNode=null; // this is the top node
	init(originalText);
	decode_(getText(),-1,o,d,a,-1,t,nodes);
    }
    public RegexNode(String originalText, Character o, Character d, Character a, Character t, RegexNode... nodes) {
	this.mark(o,d,a,t);
	this.parentNode=null; // this is the top node
	init(originalText);
	decode_(getText(),-1,o,d,a,-1,t,nodes);
    }
    /**
     * Private constructor used to create actual nodes in the RegexNode tree. 
      * @param  OriginalText
      *         The text that we want to search and process.
      * @param  parentNode
      *         Parent node.
      * @param  parentNodeStartIndex
      *         index position in parent node resultingText where this node starts.
      * @param  parentNodeEndIndex
      *         index position in parent node resultingText where this node ends.
      */
    private RegexNode(RegexNode parentNode, 
			Integer parentNodeStartIndex, 
			Integer parentNodeEndIndex) {
	this.parentNode=parentNode;
	this.parentNodeStartIndex=parentNodeStartIndex;
	this.parentNodeEndIndex=parentNodeEndIndex;
	if (parentNodeStartIndex == -1 & parentNodeEndIndex==-1) { // used to make firstChild and lastChild. 
	    this.identification=-99;
	} else {
	    init(parentNode.resultText.substring(parentNodeStartIndex,parentNodeEndIndex));
	}	
    }

    /**
     * Private constructor used to create actual nodes in the RegexNode tree. 
      * @param  OriginalText
      *         The text that we want to search and process.
      * @param  parentNode
      *         Parent node.
      * @param  parentNodeStartIndex
     *          index position in parent node resultingText where this node starts.
      * @param  parentNodeEndIndex
      *         index position in parent node resultingText where this node ends.
      */
   private RegexNode(String originalText,
			RegexNode parentNode, 
			Integer parentNodeStartIndex, 
			Integer parentNodeEndIndex) {
	this.parentNode=parentNode;
	this.parentNodeStartIndex=parentNodeStartIndex;
	this.parentNodeEndIndex=parentNodeEndIndex;
	if (parentNodeStartIndex == -1 & parentNodeEndIndex==-1) { // used to make firstChild and lastChild. 
	    this.identification=-99;
	} else {
	    init(originalText);
	}	
    }

    /**
     * Public constructor that duplicates given node 
      * @param  node
      *         The node which should be duplicated.
      */
    public RegexNode(RegexNode node) {
	this.parentNode=null; // this is the top node
	init(node.getText());
	setNodeName(node.getNodeName());
	// copy groups
	nMatchGroups=node.nMatchGroups;
	for(int ii=0;ii<=nMatchGroups;ii++) {
	    matchStartIndexShifted.put(ii,node.matchStartIndexShifted.get(ii));
	    matchEndIndexShifted.put(ii,node.matchEndIndexShifted.get(ii));
	    matchStartIndexOriginal.put(ii,node.matchStartIndexOriginal.get(ii));
	    matchEndIndexOriginal.put(ii,node.matchEndIndexOriginal.get(ii));
	    matchGroups.put(ii,node.matchGroups.get(ii));
	}
	RegexNode child=node.firstChild.nextSibling;
	if (child!=node.lastChild) {
	    RegexNode duplicateChild = child.duplicate(this);
	    lastChild.prependChain(duplicateChild);
	    child=child.nextSibling;
	}
	ignored=node.ignored;
    }
    // 
    //******************** A R G U M E N T S ******************
    //
    public RegexNode node(String node) {
	this._node=node;
	return this;
    }
    public RegexNode label(String label) {
	this._label=label;
	return this;
    }
    public RegexNode tag(String tag) {
	this._tag=tag;
	return this;
    }
    public RegexNode pattern(String pattern) {
	this._pattern=pattern;
	return this;
    }
    public RegexNode replacement(String replacement) {
	this._replacement=replacement;
	return this;
    }
    public RegexNode attribute(String att) {
	this._att=att;
	return this;
    }
    public RegexNode path(String... path) {
	this._path=path;
	return this;
    }
    public RegexNode path(String[] name1, String... name2) {
	return path(concat(name1,name2));
    }
    public RegexNode assignMark (Character o) {
	// <tag><delimiter>label<assign>string<delimiter>
	// "this is a ¤;name:test;"  equivalent to "this is a test"
	this._o=o; // assign,
	return this;
    }
    public RegexNode delimiterMark (Character d) {
	// <tag><delimiter>label<assign>string<delimiter>
	// "this is a ¤;name:test;"  equivalent to "this is a test"
	this._d=d; // delimeter
	return this;
    }
    public RegexNode tagMark (Character a) {
	// <tag><delimiter>label<assign>string<delimiter>
	// "this is a ¤;name:test;"  equivalent to "this is a test"
	this._a=a; // tag
	return this;
    }
    public RegexNode nodeMark (Character t) {
	// <tag><delimiter>label<assign>string<delimiter>
	// "this is a ¤;name:test;"  equivalent to "this is a test"
	this._t=t; // tag
	return this;
    }
    public RegexNode splitMark (Character s) {
	this._s=s; // split
	return this;
    }
    public RegexNode mark (Character o) {
	return this.assignMark(o);
    }
    public RegexNode mark (Character o,Character d) {
	return this.mark(o).delimiterMark(d);
    }
    public RegexNode mark (Character o,Character d,Character a) {
	return this.mark(o,d).tagMark(a);
    }
    public RegexNode mark (Character o,Character d,Character a,Character t) {
	return this.mark(o,d,a).nodeMark(t);
    }
    //
    //******************** C O D I N G ******************
    //
    /**
     * Decoding and re-creating nodeTree 
     * @param  code
     *         The String representation of the nodeTree.
     */
    public RegexNode decode() {
	Character o=this._o;
	Character d=this._d;
	Character a=this._a;
	Character t=this._t;
	removeChildren();
	decode_(getText(),-1,o,d,a,-1,t);
	return this;
    }
    public RegexNode decode(Character o, Character d, Character a) {
	this.mark(o,d,a);
	Character t=null;
	removeChildren();
	decode_(getText(),-1,o,d,a,-1,t);
	return this;
    }
    public RegexNode decode(String code) {
	Character o=this._o;
	Character d=this._d;
	Character a=this._a;
	Character t=this._t;
	removeChildren();
	decode_(code,-1,o,d,a,-1,t);
	return this;
    }
    public Integer decode(String code,Integer pos) {
	Character o=this._o;
	Character d=this._d;
	Character a=this._a;
	Character t=null;
	return decode_(code,pos,o,d,a,-1,t); // pos=-1???
    }
    private Integer decode_(String code,Integer pos, Character o, Character d, Character a, Integer cnt, Character t, RegexNode... nodes) {
	int len=code.length();
	//System.out.format("O%s D%S A%S\n",o,d,a);
	if (pos+1 >= code.length() & o != null) {
	    System.out.format("Missing \'%s\' in \"%s\"\n",o,code); 
	}
	pos=setNodeName(code,pos,o,d,a);
	if (pos+1 >= code.length() & d != null) {
	    System.out.format("Missing \'%s\' in \"%s\"\n",d,code); 
	}
	pos=setOriginalText(code,pos,o,d,a);
	String oText=getText();
	if (t != null) {
	    if (nodes.length > cnt+1) {
		oText=getText();
		for (int ii = -1; (ii = oText.indexOf(t, ii + 1)) != -1; ) {
		    RegexNode child = new RegexNode(this,ii,ii+1);
		    insertChild(child);
		    cnt=cnt+1;
		    nodes[cnt].replace(child);
		}
	    }
	}
	if (a != null) {
	    for (int ii = -1; (ii = oText.indexOf(a, ii + 1)) != -1; ) {
		RegexNode child = new RegexNode(this,ii,ii+1);
		insertChild(child);
		Integer ret=child.decode_(code,pos,o,d,a,cnt,t,nodes);
		cnt=ret%1000;
		pos=(ret/1000);
		if (cnt==999) {
		    cnt=-1;
		    pos=pos+1;
		} else if (cnt < -1) {
		    cnt=cnt+1000;
		    pos=-1;
		}
		//System.out.format("Return %d %d %d\n",ret,cnt,pos);
	    }
	}
	this.resultText=new StringBuilder(originalText);
	return (cnt+pos*1000);
    }
    private Integer setNodeName(String code, Integer pos, Character o, Character d, Character a) {
	Integer ii=code.substring(pos+1).indexOf(o);
	String nodeName=code.substring(pos+1,pos+ii+1);
	setNodeName(nodeName);
	return pos+ii+1;
    }
    private Integer setOriginalText(String code, Integer pos, Character o, Character d, Character a) {
	Integer ii=code.substring(pos+1).indexOf(d);
	if (ii==-1) {
	    System.out.format("Unable to find \'"+d+"\' in string: \"%s\" (%d) %s\n",
			      code.substring(pos+1),pos,code);
	}
	String oText=code.substring(pos+1,pos+ii+1);
	useOriginalString(oText);
	return pos+ii+1;
    }
    /**
     * Encoding nodeTree. Note, This replaces all labels by "¤".
     * @return  code
     *         The String representation of the nodeTree.
     */
    public String encode() {
	Character o=this._o;
	Character d=this._d;
	Character a=this._a;
	setLabelAll(String.valueOf(a));
	String s=encode_(o,a,d);
	s=s+o+d+a;
	return s;
    }
    private String encode_(Character o, Character a, Character d) {
	String s=getNodeName() + o + getText()+d;
	RegexNode child=firstChild.nextSibling;
	while(child != lastChild) {
	    s=s+child.encode_(o,a,d);
	    child=child.nextSibling;
	}
	return s;
    }
    //
    //******************** D E B U G   T O O L S ******************
    //
    /**
      * Check structure integrity. Used for debugging system errors.
      */
    public void check() {
	checkTree_();
	checkAnchors_();
    }
    private void checkTree_() {
	//System.out.format("Check done.\n");
	RegexNode child=firstChild.nextSibling;
	while(child != lastChild) {
	    if (child.parentNode != this) {
		System.out.format("Child %s parent mismatch: %s %s\n",child.identification,identification, 
				  child.parentNode.identification);
	    }
	    if (child.parentNodeStartIndex < 0 || child.parentNodeEndIndex >  resultText.length()) {
		throw new IllegalStateException(String.format("Invalid child index %s\nId:%d %d\n",toString(),identification,child.identification));
	    }
	    if (child.startFoldNode != null) {
		if (child.startFoldNode.parentNode != this) {
		    System.out.format("Child %s startFoldNode has different parent: %s\n",child.identification,child.startFoldNode.identification);
		}
	    }
	    if (child.endFoldNode != null) {
		if (child.endFoldNode.parentNode != this) {
		    System.out.format("Child %s endFoldNode has different parent: %s\n",child.identification,child.endFoldNode.identification);
		}
	    }
	    child.checkTree_();
	    child=child.nextSibling;
	}
    }
    private void checkAnchors_() {
	for (Map.Entry<String, Integer> entry : anchorCnt.entrySet()) {
	    String anchorName = entry.getKey();
	    Integer cnt    = entry.getValue();
	    if (cnt == 0) {
		System.out.format("Unused Anchor %s %d\n",anchorName,cnt);
	    }
	}
    }
    /**
      * Prints the sibling chain for this node.
      */
    public void thisString() {
	RegexNode child=firstChild.nextSibling;
	while(child != lastChild) {
	    //System.out.format("Id: %d",identification);
	    if (prevSibling != null) System.out.format(" <%d",prevSibling.identification);
	    if (nextSibling != null) System.out.format(" %d>",nextSibling.identification);
	    //System.out.format("\n");
	    child=child.nextSibling;
	}
    }
    /**
      * Get the "identification" of the node.
      * The identification is most useful for debugging.
      *
      * @return Identification of the node.
      */
    public String getIdentification() {
	return String.format("%d",identification);
    }

    /**
      * Get the number of nodes defined in node tree.
      *
      * @return Number of nodes defined in node tree.
      */
    public int getMaxIdentification() {
	return maxidentification;
    }

    /**
      * Get the string representation of the sub structure of a node.
      *
      * @return a string representation of the node structure.
      */
    public String toString() {
	return toString(-1);
    }
    private String toString(RegexNode name1, RegexNode name2) {
	return toString("   ", name1, name2);
    }
    private String toString(String prefix, RegexNode name1, RegexNode name2) {
	prefix=prefix+"   ";
	String s ="";
	RegexNode child=firstChild.nextSibling;
	while (child != lastChild) {
	    if (child == name1 || child == name2) {
		s=s+prefix+child.getIdentification()+"#"+child.getNodeName()+" <<<<<<<<<\n";
	    } else {
		s=s+prefix+child.getIdentification()+"#"+child.getNodeName()+"\n";
	    }
	    if (child.contains(name1) || child.contains(name2)) {
		s=s+child.toString(prefix,name1,name2);
	    }
	    child=child.nextSibling;
	}
	return s;
    }
    public String toString(int sublevel) {
	String s=toString(sublevel,"").replaceAll("[^\\x00-\\x7F\\xA4]", "§");
	s=s+String.format("\n#### Total number of nodes defined = %d.\n",getMaxIdentification());
	return s;
    }
    /**
      * Get the string representation of the sub structure of a node.
      *
      * @param prefix
      *  The prefix used before each line in the output string.
      *
      * @return a string representation of the node structure.
      */
    private String toString(int sublevel,String prefix) {
	String itid=String.format("%s#%s",identification,node);
	String ilid=new  String(new char[itid.length()]).replace('\0', ' ');
	String t=prefix + itid + "# \"";
	String l=prefix + ilid + "   ";
	int cursor=-1;
	RegexNode child;
	if (lastChild !=null) {
	    child=lastChild.prevSibling;
	    while (child != null & child != firstChild) { // first child is not a valid child
		if (cursor+1 == child.parentNodeEndIndex) {
		    String lid=String.format("%s",child.identification);
		    String tid="]";
		    if (lid.length()>1) {
			tid=tid+new String(new char[lid.length()-1]).replace('\0', ' ');
		    }
		    t=t + tid;
		    l=l + lid;
		}
		child=child.prevSibling;
	    }
	}
	if (resultText != null) {
	    cursor++;
	    while (cursor < resultText.length()) {
		char nextChar = resultText.charAt(cursor);
		if (firstChild != null ) {
		    child=firstChild.nextSibling;
		    while(child != null & child != lastChild) {
			if (cursor == child.parentNodeStartIndex) {
			    String lid=String.format("%s",child.identification);
			    String tid="[";
			    if (lid.length()>1) {
				tid=tid+new String(new char[lid.length()-1]).replace('\0', ' ');
			    }
			    //System.out.format("Found index: %d %d\n",child.parentNodeStartIndex,child.parentNodeEndIndex);
			    t=t + tid;
			    l=l + lid;
			}
			child=child.nextSibling;    // point to next valid element in chain
		    }
		}
		if (nextChar == '\t') {
		    nextChar=' ';
		}
		if (nextChar == '\n') {
		    String lx=l.replaceAll("\\s+","");
		    if (lx.length() > 0) {
			t=t + "\n" + l + "\n     " + prefix;
		    } else {
			t=t + "\n     " + prefix;
		    }
		    l="     " + prefix;
		} else {
		    t=t + nextChar;
		    l=l + " ";
		}
		if (lastChild != null ) {
		    child=lastChild.prevSibling;
		    while (child != null & child != firstChild) { // first child is not a valid child
			if (cursor+1 == child.parentNodeEndIndex) {
			    String lid=String.format("%s",child.identification);
			    String tid="]";
			    if (lid.length()>1) {
				tid=tid+new String(new char[lid.length()-1]).replace('\0', ' ');
			    }
			    t=t + tid;
			    l=l + lid;
			}
			child=child.prevSibling;
		    }
		}
		cursor++;
	    }
	} else {
	    t=t+"**********************";
	}
	//System.out.format("\n");
	if ( firstChild != null ) {
	    child=firstChild.nextSibling;
	    while(child != null & child != lastChild) {
		if (cursor == child.parentNodeStartIndex) {
		    String lid=String.format("%s",child.identification);
		    String tid="[";
		    if (lid.length()>1) {
			tid=tid+new String(new char[lid.length()-1]).replace('\0', ' ');
		    }
		    //System.out.format("Found index: %d %d\n",child.parentNodeStartIndex,child.parentNodeEndIndex);
		    t=t + tid;
		    l=l + lid;
		}
		child=child.nextSibling;    // point to next valid element in chain
	    }
	}
	if (parentNode != null) {
	    itid=String.format(" => %d#(%d %d)",parentNode.identification,parentNodeStartIndex,parentNodeEndIndex);
	} else {
	    itid=" Root";
	};
	if (ignored) {
	    itid=itid + " #i";
	};
	if (prevSibling != null) {
	    itid=itid + String.format(" <%d ",prevSibling.identification);
	} else {
	    itid=itid + String.format(" [");
	};
	itid=itid + String.format("%d",identification);
	if (nextSibling != null) {
	    itid=itid + String.format(" %d>",nextSibling.identification);
	} else {
	    itid=itid + String.format("]");
	};
	if (nMatchGroups==0) {
	    itid=itid + " no groups";
	} else {
	    itid=itid + String.format(" %d groups",nMatchGroups);
	}
	for (Integer ii=1;ii<= nMatchGroups;ii++) {
	    itid=itid+" \"" + matchGroups.get(ii).replace("\n","|") + "\"";
	    itid=itid+String.format("(%d,%d)",matchStartIndexShifted.get(ii),matchEndIndexShifted.get(ii));
	}
	if (startFoldNode != null) {
	    itid=itid + String.format(" Start%d#",startFoldNode.identification);
	}
	if (endFoldNode != null) {
	    itid=itid + String.format(" End%d#",endFoldNode.identification);
	}

	String s = "\n" + t + "\"" + itid + ilid + "\n" + l;
	for (Map.Entry<String, Object> entry : attributes.entrySet()) {
	    String attName   = entry.getKey();
	    s=s + "\n" + prefix + "   @" + attName;
	    Object attObject = entry.getValue();
	    if (attObject instanceof String) {
		s=s + " = \"" + (String) attObject + "\"";
	    } else if (attObject instanceof Integer || 
		       attObject instanceof Double || 
		       attObject instanceof Boolean ) {
		s=s + " = " + attObject.toString();
	    } else if (attObject instanceof RegexNode) {
		RegexNode ao=(RegexNode)attObject;
		s=s + " = " + ao.getIdentification()+"#"+ao.getNodeName();
	    } else if (attObject instanceof ArrayList<?>) {
		@SuppressWarnings("unchecked") ArrayList<Object> ao = (ArrayList) attObject;
		s=s + " = {";
		int cnt=0;
		for (Object a : ao) {
		    if (cnt > 0) s=s+", ";
		    cnt++;
		    s=s + "\"" + (String)a + "\"" ;
		}
		s=s + "}";
	    } else {
		s=s + "   ***";
	    }
	}
	for (Map.Entry<Integer, RegexNode> entry : nodeChildMap.entrySet()) {
	    Integer nodeId   = entry.getKey();
	    s=s + "\n" + prefix + "   %" + nodeId.toString();
	    RegexNode nodeChild = entry.getValue();
	    s=s + " = " + nodeChild.getIdentification();
	}
	if (firstChild != null & sublevel != 0) {
	    child=firstChild.nextSibling;
	    while(child != null & child != lastChild) {
		s=s+child.toString(sublevel-1,prefix + "  ");
		child=child.nextSibling;    // point to next valid element in chain
	    }
	}
	return s;
    }
    //
    //******************** S E A R C H ,   R E P L A C E   A N D   N A V I G A T E ******************
    //
    /**
      * Get the current resulting text for this node.
      */
    public String getText() {
	return resultText.toString();
    }
    /**
      * Get the current resulting text for a child node with specified node name.
      *         
      * @param node...
      *        node name stack for the node that should be processed 
      *        starting with the parent node in the node tree, 
      *        and ending with the name of the node that will be 
      *        processed ("..." is one or more nodes, "*" is any node, 
      *        "$" indicates top of the node tree).
      */
    public String getText(String[] name1, String... name2) { // focus on next node (recursively)
	return getText(concat(name1,name2));
    }
    String getText(String... path) {
	String text=null;
	RegexNode node = getNode(path);
	if (node !=null) {
	    text=node.getText();
	}
	getNodeReset(path);
	return text;
    }
    String getTextAll(String... path) {
	if (path.length == 0) { return this.getTextAll_();} 
	String text=null;
	RegexNode node = getNode(path);
	if (node !=null) {
	    text=node.getTextAll_();
	}
	getNodeReset(path);
	return text;
    }
    /**
      * Returns the first node and resets the node-search.
      *         
      * @param node...
      *        node name path for the node that should be processed 
      *        starting with the parent node in the node tree, 
      *        and ending with the name of the node that will be 
      *        processed ("..." is one or more nodes, "*" is any node, 
      *        "$" indicates top of the node tree).
      *
      * @Return The first node with the requested node name and location.
      */
    public RegexNode getFirstNode() { // focus on next node (recursively)
	String[] path=this._path;
	return getFirstNode(path);
    }
    public RegexNode getFirstNode(String[] name1, String... name2) { // focus on next node (recursively)
	return getFirstNode(concat(name1,name2));
    }
    RegexNode getFirstNode(String... path) {
	RegexNode node = getNode(path);
	getNodeReset(path);
	return node;
    }
    public Integer getFirstLength() { // focus on next node (recursively)
	String[] path=this._path;
	return getFirstLength(path);
    }
    public Integer getFirstLength(String[] name1, String... name2) { // focus on next node (recursively)
	return getFirstLength(concat(name1,name2));
    }
    public Integer getFirstLength(String... path) {
	RegexNode node = getNode(path);
	getNodeReset(path);
	if (node != null) {
	    return node.getText().length();
	} else {
	    return null;
	}
    }
    /**
      * search for regular expression pattern in the node.
      *
      * @param patternText
      *        regular expression pattern that should be matched.
      *
      * @return true if we found the pattern, otherwise false
      */
    public boolean find() {
	String pattern=this._pattern;
	return find(pattern);
	}
    public boolean find(String patternText) {
	return seek_(replaceAnchorNames(patternText));
    }
    public void resetSeek() {
	oldPatternString=null;
    }
    public boolean seek() {
	String pattern=this._pattern;
	return seek(pattern);
    }
    public boolean seek(String patternText) {
	return seek_(replaceAnchorNames(patternText));
    }
    public boolean seekAll() {
	String pattern=this._pattern;
	String[] path=this._path;
	return seekAll(pattern,path);
    }
    public boolean seekAll(String patternText) {
	String[] path=this._path;
	return seekAll(patternText, path);
    }
    public boolean seekAll(String patternText, String[] name1, String... name2) {
	return seekAll(patternText, concat(name1,name2));
    }
    public boolean seekAll(String patternText, String... node) {
	RegexNode target=lastSeek;
	boolean bdone=false;
	while (! bdone ) {
	    if (target == null) {
		target=getNode(node);
	    };
	    if (target!= null) {
		if (target.seek(patternText)) {
		    return true;
		} else {
		    target=null;
		}
	    } else {
		bdone=true;
	    }
	}
	return false;
    }
    
    //
    //******************** A N C H O R S ******************
    //
    /**
     * Anchors are non-standard characters that can be used to create unambiguous labels.
     * Define names to represent anchors in patterns and labels. 
     * Maximum number of anchors is 1000++.
     *
     * @param anchorName
     *        name of anchor that can be used within patterns and labels.
     *
     * @param anchorValue
     *        value of anchor that can be used within patterns and labels.
     */
    public static void define(String anchorName, String anchorValue) {
	String anchor=anchorMap.get(anchorName);
	if (anchor == null) {
	    anchorMap.put(anchorName,anchorValue);
	    anchorCnt.put(anchorName,0);
	}
    }
    /**
     * Anchors are non-standard characters that can be used to create unambiguous labels.
     * Define names to represent anchors in patterns and labels. 
     * Maximum number of anchors is 1000++.
     *
     * @param anchorName
     *        name of anchor that can be used within patterns and labels.
     */
    public static void define(String anchorName) {
	String anchor=anchorMap.get(anchorName);
	if (anchor == null) {
	    //if (nrAnchor == 0) {
	    //for (int id=1; id< 256; id++) {
	    //char c=(char) id;
	    //System.out.format("Anchor id: %d is %s\n",id,String.valueOf(c));
	    //}
	    //}
	    if (nrAnchor < 128) nrAnchor=128;
	    if (nrAnchor > 159 & nrAnchor < 168) nrAnchor=168;
	    anchor=getAnchor(nrAnchor++);
	    anchorMap.put(anchorName,anchor);
	    anchorCnt.put(anchorName,0);
	}
    }
    /**
     * Anchors are non-standard characters that can be used to create unambiguous labels.
     * Replaces defined anchor names in text string (pattern or label). 
     *
     * @param text
     *        text that should have its anchor names replaced with the anchors.
     *
     * @return
     *        text with anchors instead of anchor names.
     */
    public String replaceAnchorNames(String text) {
	if (text == null) return "";
	for (Map.Entry<String, String> entry : anchorMap.entrySet()) {
	    String anchorName = entry.getKey();
	    String anchor    = entry.getValue();
	    String ntext=text.replace(anchorName,anchor);
	    if (! ntext.equals(text)) {
		anchorCnt.put(anchorName,anchorCnt.get(anchorName)+1);
	    }
	    text=ntext;
	}
	return text;
    }
    // replace labels that are anchors with their names...
    public boolean replaceLabelAnchorNames() { // hide current match 
	String[] path=this._path;
	return replaceLabelAnchorNames(path);
    }
    public boolean replaceLabelAnchorNames(String[] name1, String... name2) { // hide current match 
	return replaceLabelAnchorNames(concat(name1,name2));
    }
    public boolean replaceLabelAnchorNames(String... path) { // hide current match 
	Character split=this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNodePattern(nodePatternText);
	return replaceLabelAnchorNames_(this,0,pattern,split);
    }
    public boolean replaceLabelAnchorNames_(RegexNode root, int targetlevel, Pattern pattern, Character split) {
	boolean hit=false;
	RegexNode child=firstChild.nextSibling;
	this.marked=false;
	while (child!= lastChild & !ignored) { // last child is not a valid child
	    if (! child.ignored) {
		if (child.replaceLabelAnchorNames_(root, targetlevel, pattern,split)) {
		    hit=true;
		};
	    };
	    child=child.nextSibling;
	}
	boolean match = (! ignored);
	Integer imatch=nodeMatch(root,pattern,split);
	if (match & pattern !=null) match= imatch != null;
	if (match) {
	    // mark parent
	    RegexNode childNode=this;
	    int level =0;
	    Integer tlevel = targetlevel;
	    if (targetlevel < 0) {
		tlevel = imatch + 1 + targetlevel; // count from top, -1 being top level in node match
	    }
	    while (level < tlevel & childNode != null & childNode != root){
		level=level+1;
		childNode=childNode.parentNode;
	    }
	    if (level == targetlevel & childNode != null) {
		childNode.marked=true;
	    }
	}
	if (marked) {  // delayed processing...
	    //System.out.format("Hiding %s (%s)\n",getIdentification(),patternText);
	    String s=getNodeName();
	    s=replaceAnchorNames(s);
	    setNodeName(s);
	    hit=true;
	}
	return hit;
    }
    /**
     * Anchors are non-standard characters that can be used to create unambiguous labels.
     * Replaces defined anchors in text string (resultText). 
     *
     * @param text
     *        text that should have its anchors replaced with the anchorNames.
     *
     * @return
     *        text with anchorNames instead of anchors.
     */
    public String replaceAnchors(String text) {
	for (Map.Entry<String, String> entry : anchorMap.entrySet()) {
	    String anchorName = entry.getKey();
	    String anchor    = entry.getValue();
	    String ntext = text.replace(anchor,anchorName);
	    if (! ntext.equals(text)) {
		anchorCnt.put(anchorName,anchorCnt.get(anchorName)+1);
	    }
	    text=ntext;
	}
	return text;
    }
    public boolean replaceLabelAnchors() { // hide current match 
	String[] path=this._path;
	return replaceLabelAnchors(path);
    }
    public boolean replaceLabelAnchors(String[] name1, String... name2) { // hide current match 
	return replaceLabelAnchors(concat(name1,name2));
    }
    public boolean replaceLabelAnchors(String... path) { // hide current match 
	Character split=this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNodePattern(nodePatternText);
	return replaceLabelAnchors_(this,0,pattern,split);
    }
    public boolean replaceLabelAnchors_(RegexNode root, int targetlevel, Pattern pattern, Character split) {
	boolean hit=false;
	RegexNode child=firstChild.nextSibling;
	this.marked=false;
	while (child!= lastChild & !ignored) { // last child is not a valid child
	    if (! child.ignored) {
		if (child.replaceLabelAnchors_(root, targetlevel, pattern,split)) {
		    hit=true;
		};
	    };
	    child=child.nextSibling;
	}
	boolean match = (! ignored);
	Integer imatch=nodeMatch(root,pattern,split);
	if (match & pattern !=null) match= imatch != null;
	if (match) {
	    // mark parent
	    RegexNode childNode=this;
	    int level =0;
	    Integer tlevel = targetlevel;
	    if (targetlevel < 0) {
		tlevel = imatch + 1 + targetlevel; // count from top, -1 being top level in node match
	    }
	    while (level < tlevel & childNode != null & childNode != root){
		level=level+1;
		childNode=childNode.parentNode;
	    }
	    if (level == targetlevel & childNode != null) {
		childNode.marked=true;
	    }
	}
	if (marked) {  // delayed processing...
	    //System.out.format("Hiding %s (%s)\n",getIdentification(),patternText);
	    String s=getNodeName();
	    s=replaceAnchors(s);
	    setNodeName(s);
	    hit=true;
	}
	return hit;
    }

    /**
     * Anchors are non-standard characters that can be used to create unambiguous labels.
     *        
     * @param id
     *        the anchor id should be between 1 and 125.
     */
    public static String getAnchor(int id) { // 
	char c=(char) id;
	//System.out.format("Anchor id: %d is %s\n",id,String.valueOf(c));
	return String.valueOf(c);
    }
    public String getAnchorTable() {
	String s="";
	for (Map.Entry<String, String> entry : anchorMap.entrySet()) {
	    String anchorName = entry.getKey();
	    String anchor    = entry.getValue();
	    s=s+anchorName + "=" + anchor + "\n";
	}
	return s;
    }

    //
    //******************** T A G S ******************
    //
    /**
      * Retrieve the node name of this node.
      *
      * @return Node name of this node.
      *         
      */
    public String getNodeName() {
	return node;
    }

    /**
      * Method to find and set focus on next node in sub-tree.
      * "focus" will search children before itself.
      *
      * @param node...
      *        node name stack for the node that should be processed 
      *        starting with the parent node in the node tree, 
      *        and ending with the name of the node that will be 
      *        processed ("..." is one or more nodes, "*" is any node, 
      *        "$" indicates top of the node tree).
      */
    public RegexNode getNode() { // focus on next node (recursively)
	String[] path=this._path;
	return getNode(path);
    }
    public RegexNode getNode(String[] name1, String... name2) { // focus on next node (recursively)
	return getNode(concat(name1,name2));
    }
    public RegexNode getNode(String... path) { // focus on next node (recursively)
	Character split=this._s;
	String nodePatternText=getPatternText(path,split);
	return getNode(nodePatternText,split);
    }
    public RegexNode getNode(String nodePatternText, Character split) { // focus on next node (recursively)
	Integer sid=getNodeSid(nodePatternText);
	Pattern pattern=getNodePattern(nodePatternText);
	return getNode_(this,sid,pattern,split);
    }
    /**
      * Method to find list of nodes in sub tree.
      *
      * @param node...
      *        node name stack for the node that should be processed 
      *        starting with the parent node in the node tree, 
      *        and ending with the name of the node that will be 
      *        processed ("..." is one or more nodes, "*" is any node, 
      *        "$" indicates top of the node tree).
      */
    public ArrayList<RegexNode> getNodeAll() { // focus on next node (recursively)
	String[] path=this._path;
	return getNodeAll(path);
    }
    public ArrayList<RegexNode> getNodeAll(String[] name1, String... name2) { // focus on next node (recursively)
	return getNodeAll(concat(name1,name2));
    }
    public ArrayList<RegexNode> getNodeAll(String... path) { // focus on next node (recursively)
	Character split=this._s;
	String nodePatternText=getPatternText(path,split);
	return getNodeAll(0,nodePatternText,split);
    }
    public ArrayList<RegexNode> getNodeAll(int targetlevel) { // focus on next node (recursively)
	String[] path=this._path;
	return getNodeAll(targetlevel,path);
    }
    public ArrayList<RegexNode> getNodeAll(int targetlevel,String[] name1, String... name2) { // focus on next node (recursively)
	return getNodeAll(targetlevel,concat(name1,name2));
    }
    public ArrayList<RegexNode> getNodeAll(int targetlevel, String... path) { // focus on next node (recursively)
	Character split=this._s;
	String nodePatternText=getPatternText(path,split);
	return getNodeAll(targetlevel,nodePatternText,split);
    }
    public ArrayList<RegexNode> getNodeAll(int targetlevel, String nodePatternText, Character split) { // focus on next node (recursively)
	Pattern pattern=getNodePattern(nodePatternText);
	return getNodeAll_(this,targetlevel,pattern,split);
    }
    public ArrayList<RegexNode> getNodeAll_(RegexNode root, int targetlevel, Pattern pattern, Character split) { // unfold nodes
	ArrayList<RegexNode> ret=null;
	RegexNode child=firstChild.nextSibling;
	while (child != lastChild & !ignored) { // last child is not a valid child
	    child.marked=false;
	    if (! child.ignored) {
		ret.addAll(child.getNodeAll_(root,targetlevel,pattern,split));
	    };
	    boolean match = (! child.ignored);
	    Integer imatch=child.nodeMatch(root,pattern,split);
	    if (match & pattern !=null) match= imatch != null;
	    if (match) {
		// mark parent
		RegexNode childNode=child;
		int level =0;
		if (targetlevel < 0) {
		    Integer tlevel = imatch + 1 + targetlevel; // count from top, -1 being top level in node match
		    while (level < tlevel & childNode != null & childNode != root){
			level=level+1;
			childNode=childNode.parentNode;
		    }
		    if (level == targetlevel & childNode != null) {
			childNode.marked=true;
		    }
		} else {
		    while (level < targetlevel & childNode != null & childNode != root){
			level=level+1;
			childNode=childNode.parentNode;
		    }
		    if (level == targetlevel & childNode != null) {
			childNode.marked=true;
		    }
		}
		if (child.marked) { // delayed processing...
		    //check();
		    try {
			ret.add(child);
		    } catch (NullPointerException e) {
			System.out.format("Error getNodeAll %s\n%s\n",child.identification, toString());
			throw e;
		    }
		} else {
		    child=child.nextSibling;
		}
	    } else {
		child=child.nextSibling;
	    }
	}
	return ret;
    }

    //
    private Integer getNodeSid(String nodePatternText) {
	Integer sid=nodeSidMap.get(nodePatternText);
	if (sid == null) {
	    sid=maxsid++;
	    nodeSidMap.put(nodePatternText,sid);
	}
	return sid;
    }
    private Pattern getNodePattern(String nodePatternText) {
	Pattern pattern=nodePatternMap.get(nodePatternText);
	if (pattern == null) {
	    pattern=getNewPattern(nodePatternText);
	    nodePatternMap.put(nodePatternText,pattern);
	}
	return pattern;
    }
    /**
      * Method to reset the "getNode" search.
      *
      * @param node...
      *        node name stack for the node that should be processed 
      *        starting with the parent node in the node tree, 
      *        and ending with the name of the node that will be 
      *        processed ("..." is one or more nodes, "*" is any node, 
      *        "$" indicates top of the node tree).
      */
    public void getNodeReset(String... path) { // focus on next node (recursively)
	Character split = this._s;
	String nodePatternText=getPatternText(path,split);
	Integer sid=nodeSidMap.get(nodePatternText);
	if (sid != null) {
	    getNodeReset_(this,sid);
	}
    }
    public RegexNode getNextSibling() {
	return nextSibling;
    }
    public RegexNode getPrevSibling() {
	return prevSibling;
    }
    public RegexNode getNextSibling(String name) {
	if (name.equals(nextSibling.getNodeName())) {
	    return nextSibling;
	} else {
	    return null;
	}
    }
    public RegexNode getPrevSibling(String name) {
	if (name.equals(prevSibling.getNodeName())) {
	    return prevSibling;
	} else {
	    return null;
	}
    }
    public RegexNode getFirstChild() {
	return firstChild;
    }
    public RegexNode getLastChild() {
	return lastChild;
    }
    public RegexNode getOnlyChild() {
	RegexNode ret=firstChild.nextSibling;
	RegexNode pre=lastChild.prevSibling;
	if (ret != pre) {
	    return null;
	}
	return ret;
    }
    public RegexNode getOnlyChild(String nodeName) {
	RegexNode ret=firstChild.nextSibling;
	RegexNode pre=lastChild.prevSibling;
	if (ret != pre || ! ret.getNodeName().equals(nodeName)) {
	    return null;
	}
	return ret;
    }
    /* 
     * Returns the parent node that is the child of the target node.
     *
     */
    public RegexNode getTopChild(RegexNode child) {
	RegexNode parent=child.getParent();
	while (parent != null & parent != this) {
	    child=parent;
	    parent=child.getParent();
	}
	if (parent != this) child=null;
	return child;
    }
    public boolean contains(RegexNode child) {
	boolean contained=false;
	while (child !=null) {
	    if (child == this) {
		contained=true;
		child=null;
	    } else {
		child=child.getParent();
	    }
	}
	return contained;
    }
    public RegexNode getParentOf(String name) {
	RegexNode current=this;
	boolean found=false;
	while (current!=null & ! found) {
	    RegexNode child=current.getFirstChild().getNextSibling();
	    while (child != current.getLastChild() & ! found) {
		if (name.equals(child.getNodeName())) {
		    found=true;
		}
		child=child.getNextSibling();
	    }
	    if (! found) {
		current=current.getParent();
	    }
	}
	if (found) {
	    return current;
	} else {
	    return null;
	}
    }
    /**
     * Returns the parent to the RegexNode node.
     *
     * Note that complete path must be used to parent, 
     * use "..." for any number of levels, and "*" for any node
     *
     * @return RegexNode node of the parent.
     */
    public RegexNode getParent() {
	return parentNode;
    }
    /* 
     * Returns the parent node at specified level (1 = parent)
     *
     */
    public RegexNode getParent(int level) {
	RegexNode current=this;
	Integer plevel=0;
	while (current != null & plevel < level) {
	    current=current.getParent();
	    plevel++;
	}
	return current;
    }

    public RegexNode getParent(String[] name1, String... name2) { // focus on next node (recursively)
	return getParent(concat(name1,name2));
    }
    public RegexNode getParent(String... path) {
	Character split = this._s;
	String nodePatternText=getPatternText(path,split);
	RegexNode child=getNode(nodePatternText,split);
	if (child != null) {
	    Pattern pattern=getNodePattern(nodePatternText);
	    Integer toplevel=child.nodeMatch(this,pattern,split); // redo the match
	    if (toplevel != null) {
		return getParent(toplevel);
	    } else {
		return null; // this should never happen!!!!
	    }
	} else {
	    return null;
	}
    }
    public int getParentStartIndex() {
	return parentNodeStartIndex;
    }
    public int getParentEndIndex() {
	return parentNodeEndIndex;
    }
    public int count(String match) { // focus on next node (recursively)
	String[] path=this._path;
	return count(match,path);
    }
    public int count(String match, String[] name1, String... name2) { // focus on next node (recursively)
	return count(match,concat(name1,name2));
    }
    public int count(String match,String... path) {
	int cnt=0;
	RegexNode child=getNode(path);
	while (child != null) {
	    if (match.equals("") || match.equals(child.getText())) {
		cnt=cnt+1;
	    }
	    child=getNode(path);
	}
	return cnt;
    }

    /**
      * Removes current node from its sibling chain,
      * so that siblings do not see this node any more.
      * The label remains in place in the parent node,
      * and this node still sees its old siblings.
      * 
      * @return false when node is the top node.
      */
    public boolean rmNode() { // remove node and discard any changes herein, leave label unchanged
	if (parentNode == null) {return false;}; // top node
	//unhideAll();
	int length=getText().length();
	setLabel_(String.format("%1$"+length+ "s", ""));
	unlink(); // remove from sibling chain
	return true; // returns false when current node is top node
    }
    public boolean rmNode(String label) { // remove node and discard any changes herein after setting new label
	if (parentNode == null) {return false;}; // top node
	setLabel_(label);
	unlink(); // remove from sibling chain
	return true; // returns false when current node is top node
    }
    public boolean rmNodeAll(String label,String[] name1, String... name2) { // focus on next node (recursively)
	return rmNodeAll(label,concat(name1,name2));
    }
    public boolean rmNodeAll(String label,String... path) { // focus on next node (recursively)
	Character split=this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNodePattern(nodePatternText);
	return rmNodeAll_(label,this,0,pattern,split);
    }
    public boolean rmNodeAll_(String label, RegexNode root, int targetlevel, Pattern pattern, Character split) { // unfold nodes
	boolean ret=false;
	RegexNode child=firstChild.nextSibling;
	while (child != lastChild & !ignored) { // last child is not a valid child
	    child.marked=false;
	    RegexNode nextChild=child.nextSibling;
	    if (! child.ignored) {
		if (child.rmNodeAll_(label,root,targetlevel,pattern,split)) {
		    ret=true;
		};
	    };
	    boolean match = (! child.ignored);
	    Integer imatch=child.nodeMatch(root,pattern,split);
	    if (match & pattern !=null) match= imatch != null;
	    if (match) {
		// mark parent
		RegexNode childNode=child;
		int level =0;
		if (targetlevel < 0) {
		    Integer tlevel = imatch + 1 + targetlevel; // count from top, -1 being top level in node match
		    while (level < tlevel & childNode != null & childNode != root){
			level=level+1;
			childNode=childNode.parentNode;
		    }
		    if (level == targetlevel & childNode != null) {
			childNode.marked=true;
		    }
		} else {
		    while (level < targetlevel & childNode != null & childNode != root){
			level=level+1;
			childNode=childNode.parentNode;
		    }
		    if (level == targetlevel & childNode != null) {
			childNode.marked=true;
		    }
		}
		if (child.marked) { // delayed processing...
		    //check();
		    try {
			ret=true;
			child.rmNode(label);
		    } catch (NullPointerException e) {
			System.out.format("Error getNodeAll %s\n%s\n",child.identification, toString());
			throw e;
		    }
		}
	    }
	    child=nextChild;
	}
	return ret;
    }



    //
    //******************** C H A N G E   T E X T   A N D   L A B E L S ******************
    //
    /**
      * method to add node to the end of this node.
      * @param child
      *        child is inserted at the end of this node
      * @param label
      *        label used in this node in replace of child node.
      */
    public RegexNode prependChild(RegexNode child) { // put child first in this node
	prependChild(child,child.getLabel());
	return child;
    }
    public RegexNode prependChild(RegexNode child, String label) { // put child first in this node
	label=replaceAnchorNames(label);
	if (child.parentNode != null) child.setLabel("");
	child.unlink();
	Plan plan=prependText(0,label);
	child.parentNode=this;
	child.shiftIndexesSoTheyMatch(plan);
	firstChild.appendChain(child); // first element
	return child;
    }
    RegexNode prependChild(String label,String... path) {
	RegexNode node=getNode(path);
	while (node != null) {
	    RegexNode newNode=getNode(path);
	    prependChild(node,label);
	    node=newNode;
	}
	return node;
    }
    public RegexNode prependSibling(RegexNode sibling) { // put sibling node just before this node
	return prependSibling(sibling,sibling.getLabel());
    }
    public RegexNode prependSibling(RegexNode sibling, String label) { // put sibling node just before this node
	if (startFoldNode != null) return startFoldNode.prependSibling(sibling,label);
	return prependSibling_(sibling, label);
    }
    public RegexNode prependUnfoldedSibling(RegexNode sibling) { // put sibling node just before this node
	return prependUnfoldedSibling(sibling,sibling.getLabel());
    }
    public RegexNode prependUnfoldedSibling(RegexNode sibling, String label) { // put sibling node just before this node
	return prependSibling_(sibling, label);
    }
    private RegexNode prependSibling_(RegexNode sibling, String label) { // put sibling node just before this node
	label=replaceAnchorNames(label);
	sibling.unlink();
	Plan plan=prependSiblingText(label);
	if (plan == null) return null; // this is the root
	sibling.parentNode=parentNode;
	sibling.shiftIndexesSoTheyMatch(plan);
	prependChain(sibling); // add to sibling chain
	return sibling;
    }
    public RegexNode insertChild(RegexNode child) { // add child to sibling chain after this node
	child.unlink();
	int startIndex=child.parentNodeStartIndex;
	int endIndex=child.parentNodeEndIndex;
	Plan plan=planReplacement(resultText.substring(startIndex,endIndex),startIndex,endIndex);
	RegexNode pchild=getChildBefore(endIndex);
	pchild.shiftSiblingChainIndexes(plan); // shift indexes
	shiftMatchIndexes(plan);         // shift group indexes
	swap_(plan);
	child.parentNode=this;
	child.shiftIndexesSoTheyMatch(plan);
	pchild.appendChain(child); // add to sibling chain
	return child;
    }

    public RegexNode insertChild(RegexNode child, String label, int startIndex, int endIndex) { // add child to sibling chain after this node
	label=replaceAnchorNames(label);
	child.unlink();
	Plan plan=planReplacement(label,startIndex,endIndex);
	RegexNode pchild=getChildBefore(endIndex);
	pchild.shiftSiblingChainIndexes(plan); // shift indexes
	shiftMatchIndexes(plan);         // shift group indexes
	swap_(plan);
	child.parentNode=this;
	child.shiftIndexesSoTheyMatch(plan);
	pchild.appendChain(child); // add to sibling chain
	return child;
    }

    public RegexNode appendChild(RegexNode child, String label, int group) { // add child to sibling chain after this node
	label=replaceAnchorNames(label);
	child.unlink();
	Plan plan=planReplacement(label,end(group),end(group));
	RegexNode pchild=getChildBefore(end(group));
	pchild.shiftSiblingChainIndexes(plan); // shift indexes
	shiftMatchIndexes(plan);         // shift group indexes
	swap_(plan);
	child.parentNode=this;
	child.shiftIndexesSoTheyMatch(plan);
	pchild.appendChain(child); // add to sibling chain
	return child;
    }
    /**
      * method to add node to the end of this node.
      * @param child
      *        child is inserted at the end of this node
      * @param label
      *        label used in this node in replace of child node.
      */
    public RegexNode appendChild(RegexNode child) { // put child last in this node
	appendChild(child,child.getLabel());
	return child;
    }
    public RegexNode appendChild(RegexNode child, String label) { // put child last in this node
	label=replaceAnchorNames(label);
	if (child.parentNode != null) child.setLabel("");
	child.unlink();
	Plan plan=appendText(0,label);
	child.parentNode=this;
	child.shiftIndexesSoTheyMatch(plan);
	lastChild.prependChain(child); // last element
	return child;
    }
    public RegexNode appendSibling(RegexNode sibling) { // put sibling node just before this node
	return appendSibling(sibling, sibling.getLabel());// put sibling node just before this node
    }
    public RegexNode appendSibling(RegexNode sibling, String label) { // put sibling node just before this node
	if (endFoldNode != null) return endFoldNode.appendSibling(sibling,label);
	return appendSibling_(sibling, label);
    }
    public RegexNode appendUnfoldedSibling(RegexNode sibling) { // put sibling node just before this node
	return appendUnfoldedSibling(sibling, sibling.getLabel());// put sibling node just before this node
    }
    public RegexNode appendUnfoldedSibling(RegexNode sibling, String label) { // put sibling node just before this node
	return appendSibling_(sibling, label);
    }
    public RegexNode appendSibling_(RegexNode sibling, String label) { // put sibling node just before this node
	label=replaceAnchorNames(label);
	sibling.unlink();
	Plan plan=appendSiblingText(label);
	if (plan == null) return null; // this is the root
	sibling.parentNode=parentNode;
	sibling.shiftIndexesSoTheyMatch(plan);
	appendChain(sibling); // add to sibling chain
	sibling.setLabel_(label);
	return sibling;
    }
    /**
      * Inserts text at the beginning of the node.
      *
      * @param text
      *  text string to be inserted at the beginning of the node.
      */
    public Plan prependText(String text) {
	return prependText(0,text);
    }
    public Plan prependText(int len,String text) {
	Plan plan=planReplacement(text,0,len);
	firstChild.shiftSiblingChainIndexes(plan); // shift indexes
	shiftMatchIndexes(plan);         // shift group indexes
	swap_(plan);
	return plan;
    }
    public String prependChildLabel(RegexNode child, String mark) {
	RegexNode topChild=getTopChild(child);
	String label=mark+topChild.getLabel();
	topChild.setLabel_(label);
	return label;
    }
    public String appendChildLabel(RegexNode child, String mark) {
	RegexNode topChild=getTopChild(child);
	String label=topChild.getLabel()+mark;
	topChild.setLabel_(label);
	return label;
    }
    /**
      * Inserts text at the beginning of the given group.
      *
      * @param text
      *  text string to be inserted.
      *
      * @param group
      *  group number (0 is the whole match)
      */
    public Plan prependText(String text,int group) {
	Plan plan=planReplacement(text,start(group),start(group));
	RegexNode child=getChildBefore(start(group));
	child.shiftSiblingChainIndexes(plan); // shift indexes
	shiftMatchIndexes(plan);         // shift group indexes
	swap_(plan);
	return plan;
    }
    public Plan prependSiblingText(String text) {
	if (parentNode == null) return null;
	Plan plan=planReplacement(text,parentNodeStartIndex,parentNodeStartIndex);
	RegexNode child=prevSibling;
	child.shiftSiblingChainIndexes(plan);      // shift indexes
	parentNode.shiftMatchIndexes(plan);         // shift group indexes
	parentNode.swap_(plan);
	//System.out.format("Prepend sibling: %s\n%s\n",plan.toString(),parentNode.toString());
	return plan;
    }
    public Plan appendSiblingText(String text) {
	if (parentNode == null) return null;
	Plan plan=planReplacement(text,parentNodeEndIndex,parentNodeEndIndex);
	RegexNode child=prevSibling;
	child.shiftSiblingChainIndexes(plan);      // shift indexes
	parentNode.shiftMatchIndexes(plan);         // shift group indexes
	parentNode.swap_(plan);
	return plan;
    }
    /**
      * method to add node to the end of this node.
      * @param child
      *        child is inserted at the end of this node
      * @param label
      *        label used in this node in replace of child node.
      */
    /**
      * Inserts text after the given group.
      *
      * @param text
      *  text string to be inserted at the beginning of the node.
      *
      * @param group
      *  group number (0 is the whole match)
      */
    public Plan appendText(String text,int group) {
	Plan plan=planReplacement(text,end(group),end(group));
	RegexNode child=getChildBefore(end(group));
	child.shiftSiblingChainIndexes(plan); // shift indexes
	shiftMatchIndexes(plan);         // shift group indexes
	swap_(plan);
	return plan;
    }
    public Plan appendText(String text,RegexNode child) {
	RegexNode parent=child.parentNode;
	Plan plan=planReplacement(text,child.getParentEndIndex(),child.getParentEndIndex());
	child.shiftSiblingChainIndexes(plan); // shift indexes
	parent.shiftMatchIndexes(plan);         // shift group indexes
	parent.swap_(plan);
	return plan;
    }
    public Plan prependText(String text,RegexNode child) {
	RegexNode parent=child.parentNode;
	Plan plan=planReplacement(text,child.getParentStartIndex(),child.getParentStartIndex());
	child=child.prevSibling;
	child.shiftSiblingChainIndexes(plan); // shift indexes
	parent.shiftMatchIndexes(plan);         // shift group indexes
	parent.swap_(plan);
	return plan;
    }
    /**
      * Inserts text at the end of the node.
      *
      * @param text
      *  text string to be inserted.
      */
    public Plan appendText(String text) {
	Plan plan=planReplacement(text,resultText.length(),resultText.length());
	swap_(plan);
	return plan;
    }
    public Plan appendText(int len, String text) {
	Plan plan=planReplacement(text,resultText.length()-len,resultText.length());
	swap_(plan);
	return plan;
    }
    public void appendText(String text, String[] name1, String... name2) { // focus on next node (recursively)
	appendText(text,concat(name1,name2));
    }
    public void appendText(String text, String... path) {
	RegexNode node=getNode(path);
	while (node != null) {
	    node.appendText(text);
	    node=getNode(path);
	}
    }
    public void prependText(String text, String[] name1, String... name2) { // focus on next node (recursively)
	prependText(text,concat(name1,name2));
    }
    public void prependText(String text, String... path) {
	RegexNode node=getNode(path);
	while (node != null) {
	    node.prependText(text);
	    node=getNode(path);
	}
    }
    /* 
     * Replaces a node that is in a nodeTree.
     *
     * @return victim
     *      The node that has been replaced
     */
    public RegexNode replace(RegexNode victim) {
	RegexNode hbuff=this.parentNode;
	this.parentNode=victim.parentNode;
	victim.parentNode=hbuff;
	//
	Integer ibuff=this.parentNodeStartIndex;
	this.parentNodeStartIndex=victim.parentNodeStartIndex;
	victim.parentNodeStartIndex=ibuff;
	//
	ibuff=this.parentNodeEndIndex;
	this.parentNodeEndIndex=victim.parentNodeEndIndex;
	victim.parentNodeEndIndex=ibuff;
	//
	hbuff=this.nextSibling;
	this.nextSibling=victim.nextSibling;
	victim.nextSibling=hbuff;
	//
	hbuff=this.prevSibling;
	this.prevSibling=victim.prevSibling;
	victim.prevSibling=hbuff;
	//
	hbuff=this.startFoldNode;
	this.startFoldNode=victim.startFoldNode;
	victim.startFoldNode=hbuff;
	//
	hbuff=this.endFoldNode;
	this.endFoldNode=victim.endFoldNode;
	victim.endFoldNode=hbuff;
	//
	if (this.nextSibling != null) this.nextSibling.prevSibling=this;
	if (this.prevSibling != null) this.prevSibling.nextSibling=this;
	if (this.startFoldNode != null) this.startFoldNode.endFoldNode=this;
	if (this.endFoldNode != null) this.endFoldNode.startFoldNode=this;
	//
	if (victim.nextSibling != null) victim.nextSibling.prevSibling=victim;
	if (victim.prevSibling != null) victim.prevSibling.nextSibling=victim;
	if (victim.startFoldNode != null) victim.startFoldNode.endFoldNode=victim;
	if (victim.endFoldNode != null) victim.endFoldNode.startFoldNode=victim;
	//
	return victim;
    }
    /**
      * Changes node name.
      *        
      * @param node
      *        New node name.
      */
    public RegexNode name(String node) {
	setNodeName(node);
	return this;
    };
    public boolean setNodeName(String node) {
	boolean ret=true;
	if (node.equals(this.node)) {
	    ret=false;
	} else {
	    this.node=node;
	};
	this._node=node;
	return ret;
    }
    /**
      * Changes node name.
      *        
      * @param newnode
      *        New node name.
      *
      * @param node...
      *        node name stack for the node that should be processed 
      *        starting with the parent node in the node tree, 
      *        and ending with the name of the node that will be 
      *        processed ("..." is one or more nodes, "*" is any node, 
      *        "$" indicates top of the node tree).
      *
      * @return returns true if at least one node was given new node name.
      */
    public boolean setNodeNameAll() {
	String newnode=this._node;
	String[] path=this._path;
	return setNodeNameAll(newnode, path);
    }
    public boolean setNodeNameAll(String newnode) {
	String[] path=this._path;
	return setNodeNameAll(newnode, path);
    }
    public boolean setNodeNameAll(String newnode, String[] name1, String... name2) {
	return setNodeNameAll(newnode, concat(name1,name2));
    }
    public boolean setNodeNameAll(String newnode, String... path) {
	Character split = this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNewPattern(nodePatternText);
	return setNodeNameAll_(this, newnode, pattern, split);
    }
    /**
      * Changes label in parent node.
      *        
      * @param label
      *        New label.
      */
    public String setLabel(String label) {
	label=replaceAnchorNames(label);
	return setLabel_(label);
    }
    private String setLabel_(String label) {
	if (parentNode != null) {
	    return parentNode.setChildLabel(this,label);
	} else {
	    return null;
	}
    }
    private String setLabel_(Character label) {
	if (parentNode != null) {
	    return parentNode.setChildLabel(this,label);
	} else {
	    return null;
	}
    }
    public String getLabel() {
	if (parentNode != null) {
	    return parentNode.resultText.substring(parentNodeStartIndex,parentNodeEndIndex);
	} else {
	    return null;
	}
    }
    /**
      * Changes label of child node.
      *        
      * @param child
      *        The child which will get a new label.
      *        
      * @param label
      *        New label.
      */
    public String setChildLabel(RegexNode child, String label) {
	//System.out.format("SetChildLabel: %s\n", child.toString());
	// make replacement plan
	Plan plan=planReplacement(label,child.parentNodeStartIndex,child.parentNodeEndIndex);
	child.shiftIndexesSoTheyMatch(plan); // shifts only indexes in this node, assuming node matches plan
	//System.out.format("SetChildLabel: %s\n%s\n", plan.toString(),child.toString());
	child.shiftSiblingChainIndexes(plan);  // shift the sibling indexes of the next siblings (not previous!)
	shiftMatchIndexes(plan);    // shift grup indexes
	//System.out.format("SetChildLabel: %s\n%s\n", plan.toString(),child.toString());
	return swap_(plan);         // update result text
    }
    private String fillSpace(Integer length,Character c) {
	if (length == null) length=0;
	if (length == 0) {
	    return "";
	} else {
	    return String.format("%1$"+length+ "s", c);
	}
    }
    public String setChildLabel(RegexNode child, Character c) {
	//System.out.format("SetChildLabel: %s\n", child.toString());
	// make replacement plan
	String label=fillSpace(child.getText().length(),c);
	Plan plan=planReplacement(label,child.parentNodeStartIndex,child.parentNodeEndIndex);
	child.shiftIndexesSoTheyMatch(plan); // shifts only indexes in this node, assuming node matches plan
	//System.out.format("SetChildLabel: %s\n%s\n", plan.toString(),child.toString());
	child.shiftSiblingChainIndexes(plan);  // shift the sibling indexes of the next siblings (not previous!)
	shiftMatchIndexes(plan);    // shift grup indexes
	//System.out.format("SetChildLabel: %s\n%s\n", plan.toString(),child.toString());
	return swap_(plan);         // update result text
    }
    /**
      * Changes all labels
      *        
      * @param label
      *        New label.
      *        
      * @param node...
      *        node name stack for the node that should be processed 
      *        starting with the parent node in the node tree, 
      *        and ending with the name of the node that will be 
      *        processed ("..." is one or more nodes, "*" is any node, 
      *        "$" indicates top of the node tree).
      */
    public boolean setLabelAll() {
	String label=this._label;
	String[] path=this._path;
	return setLabelAll(label, path);
    }
    public boolean setLabelAll(String label, String[] name1, String... name2) {
	return setLabelAll(label, concat(name1, name2));
    }
    public boolean setLabelAll(String label, String... path) {
	label=replaceAnchorNames(label);
	Character split=this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNodePattern(nodePatternText);
	return setLabelAll_(this,0,label,pattern,split);
    }

    public boolean setLabelAll(String label, int targetlevel, String[] name1, String... name2) {
	return setLabelAll(label, targetlevel, concat(name1, name2));
    }
    public boolean setLabelAll(String label, int targetlevel, String... path) {
	label=replaceAnchorNames(label);
	Character split=this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNodePattern(nodePatternText);
	return setLabelAll_(this,targetlevel,label,pattern,split);
    }

    public boolean setLabelAll(Character label, String[] name1, String... name2) {
	return setLabelAll(label, concat(name1, name2));
    }
    public boolean setLabelAll(Character label, String... path) {
	Character split=this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNodePattern(nodePatternText);
	return setLabelAll_(this,0,label,pattern,split);
    }

    public boolean setLabelAll(Character label, int targetlevel, String[] name1, String... name2) {
	return setLabelAll(label, targetlevel, concat(name1, name2));
    }
    public boolean setLabelAll(Character label, int targetlevel, String... path) {
	Character split=this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNodePattern(nodePatternText);
	return setLabelAll_(this,targetlevel,label,pattern,split);
    }
    /**
      * Sets label on all nodes with the specified node pattern.
      * Only the calling node is searched.
      *
      * @param label
      *        label to replace the hidden text in the search text.
      *
      * @param targetlevel
      *        number of targetlevels to process
      *        Only the node itself is processed when targetlevel = 0.
      *        All targetlevels are processed when targetlevel = -1.
      *
      * @param subNodes
      *        number of targetlevels to process below the target-nodes.
      *        Only the target-nodes themselves are processed when subNodes = 0.
      *        All targetlevels below target-nodes are processed when subNodes = -1.
      *
      * @param node...
      *        node name stack for the node that should be processed 
      *        starting with the parent node in the node tree, 
      *        and ending with the name of the node that will be 
      *        processed ("..." is one or more nodes, "*" is any node, 
      *        "$" indicates top of the node tree).
      */
    private boolean setLabelAll_(RegexNode root, int targetlevel, String label,Pattern pattern, Character split) { // hide current match 
	boolean hit=false;
	RegexNode child=firstChild.nextSibling;
	while (child!= lastChild & !ignored) { // last child is not a valid child
	    child.marked=false;
	    if (! child.ignored) {
		if (child.setLabelAll_(root, targetlevel, label, pattern,split)) {
		    hit=true;
		};
	    };
	    boolean match = (! child.ignored);
	    Integer imatch=child.nodeMatch(root,pattern,split);
	    if (match & pattern !=null) match= imatch != null;
	    if (match) {
		// mark parent
		RegexNode childNode=child;
		int level =0;
		Integer tlevel = targetlevel;
		if (targetlevel < 0) {
		    tlevel = imatch + 1 + targetlevel; // count from top, -1 being top level in node match
		}
		while (level < tlevel & childNode != null & childNode != root){
		    level=level+1;
		    childNode=childNode.parentNode;
		}
		if (level == targetlevel & childNode != null) {
		    childNode.marked=true;
		}
	    }
	    if (child.marked) {  // delayed processing...
               try {
		   child.setLabel_(label);
                } catch (NullPointerException e) {
                    System.out.format("Error hiding %s\n%s\n",child.identification, toString());
                    throw e;
                }
		hit=true;
	    }
	    child=child.nextSibling;
	}
	return hit;
    }
    //copies character over label
    private boolean setLabelAll_(RegexNode root, int targetlevel, Character label, Pattern pattern, Character split) { // hide current match 
	boolean hit=false;
	RegexNode child=firstChild.nextSibling;
	while (child!= lastChild & !ignored) { // last child is not a valid child
	    child.marked=false;
	    if (! child.ignored) {
		if (child.setLabelAll_(root, targetlevel, label, pattern,split)) {
		    hit=true;
		};
	    };
	    boolean match = (! child.ignored);
	    Integer imatch=child.nodeMatch(root,pattern,split);
	    if (match & pattern !=null) match= imatch != null;
	    if (match) {
		// mark parent
		RegexNode childNode=child;
		int level =0;
		Integer tlevel = targetlevel;
		if (targetlevel < 0) {
		    tlevel = imatch + 1 + targetlevel; // count from top, -1 being top level in node match
		}
		while (level < tlevel & childNode != null & childNode != root){
		    level=level+1;
		    childNode=childNode.parentNode;
		}
		if (level == targetlevel & childNode != null) {
		    childNode.marked=true;
		}
	    }
	    if (child.marked) {  // delayed processing...
               try {
		   child.setLabel_(label);
                } catch (NullPointerException e) {
                    System.out.format("Error hiding %s\n%s\n",child.identification, toString());
                    throw e;
                }
		hit=true;
	    }
	    child=child.nextSibling;
	}
	return hit;
    }

    public boolean markLabelAll(String... name2) {
	return markLabelAll((String) null, (String) null, 0, name2);
    }
    public boolean markLabelAll(String startmark, String endmark, String[] name1, String... name2) {
	return markLabelAll(startmark, endmark, 0, concat(name1, name2));
    }
    public boolean markLabelAll(String startmark, String endmark, String... path) {
	return markLabelAll(startmark, endmark, 0, path);
    }
    public boolean markLabelAll(int targetlevel, String[] name1, String... name2) {
	return markLabelAll((String) null, (String) null, targetlevel, concat(name1, name2));
    }
    public boolean markLabelAll(int targetlevel, String... name2) {
	return markLabelAll((String) null, (String) null, targetlevel, name2);
    }
    public boolean markLabelAll(String startmark, int targetlevel, String[] name1, String... name2) {
	return markLabelAll(startmark, startmark, targetlevel, concat(name1, name2));
    }
    public boolean markLabelAll(String startmark, String endmark, int targetlevel, String[] name1, String... name2) {
	return markLabelAll(startmark, endmark, targetlevel, concat(name1, name2));
    }
    public boolean markLabelAll(String startmark, String endmark, int targetlevel, String... path) {
	Character split=this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNodePattern(nodePatternText);
	return markLabelAll_(startmark, endmark, targetlevel, this, pattern, split);
    }
    private boolean markLabelAll_(String startmark, String endmark, int targetlevel, RegexNode root, Pattern pattern, Character split) {
	boolean hit=false;
	RegexNode child=firstChild.nextSibling;
	while (child!= lastChild & !ignored) { // last child is not a valid child
	    if (! child.ignored) {
		if (child.markLabelAll_(startmark, endmark, targetlevel, root, pattern, split)) {
		    hit=true;
		};
	    };
	    if (markLabel_(startmark, endmark, targetlevel, root, pattern, split)) {
		hit=true;
	    }
	    child=child.getNextSibling();
	}
	return hit;
    }
    public boolean markLabelAll(RegexNode child, int targetlevel, String... path) {
	return markLabelAll(child, (String) null, (String) null, targetlevel, path);
    }
    public boolean markLabelAll(RegexNode child, String startmark, int targetlevel, String... path) {
	return markLabelAll(child, startmark, startmark, targetlevel, path);
    }
    public boolean markLabelAll(RegexNode child, String startmark, String endmark, int targetlevel, String... path) {
	Character split=this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNodePattern(nodePatternText);
	return child.markLabel_(startmark, endmark, targetlevel, this, pattern, split);
    }
    public boolean markLabelAll(ArrayList<RegexNode>  nodeList, int targetlevel, String[] name1, String... name2) {
	return markLabelAll(nodeList, null, null, targetlevel, concat(name1, name2));
    }
    public boolean markLabelAll(ArrayList<RegexNode>  nodeList, String startmark, int targetlevel, String[] name1, String... name2) {
	return markLabelAll(nodeList, startmark, startmark, targetlevel, concat(name1, name2));
    }
    public boolean markLabelAll(ArrayList<RegexNode>  nodeList, String startmark, String endmark, int targetlevel, String[] name1, String... name2) {
	return markLabelAll(nodeList, startmark, endmark, targetlevel, concat(name1, name2));
    }
    public boolean markLabelAll(ArrayList<RegexNode>  nodeList, int targetlevel, String... path) {
	return markLabelAll(nodeList, null, null, targetlevel, path);
    }
    public boolean markLabelAll(ArrayList<RegexNode>  nodeList, String startmark, int targetlevel, String... path) {
	return markLabelAll(nodeList, startmark, startmark, targetlevel, path);
    }
    public boolean markLabelAll(ArrayList<RegexNode>  nodeList, String startmark, String endmark, int targetlevel, String... path) {
	Character split=this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNodePattern(nodePatternText);
	return markLabelAll_(nodeList, startmark, endmark, targetlevel, this, pattern, split);
    }
    private boolean markLabelAll_(ArrayList<RegexNode>  nodeList, String startmark, String endmark, int targetlevel, 
				  RegexNode root, Pattern pattern, Character split) {
	if (nodeList == null) return false;
	boolean hit=false;
	for (RegexNode child : nodeList) {
	    if (child.markLabel_(startmark, endmark, targetlevel, root, pattern, split)) {
		hit=true;
	    }
	}
	return hit;
    }
    private boolean markLabel_(String startmark, String endmark, int targetlevel, 
			       RegexNode root, Pattern pattern, Character split) {
	//System.out.format("Request to mark label %s\n",getIdentification());
	boolean hit=false;
	boolean match = (! ignored);
	Integer imatch=nodeMatch(root,pattern,split);
	if (match & pattern !=null) match= imatch != null;
	if (match) {
	    // mark parent
	    RegexNode childNode=this;
	    Integer tlevel=targetlevel;
	    if (targetlevel < 0) {
		tlevel = imatch + 1 + targetlevel; // count from top, -1 being top level in node match
	    }
	    //System.out.format("Does node %s match level %d (max=%d)...?",getIdentification(),tlevel,imatch);
	    int level =0;
	    while (level < tlevel & childNode != null & childNode != root){
		level=level+1;
		childNode=childNode.parentNode;
	    }
	    if (level == tlevel & childNode != null) {
		//System.out.format(" Match found for.\n",childNode.getIdentification());
		try {
		    String label=childNode.getLabel();
		    String value=(String) childNode.getAttribute("labelMarked");
		    if (value == null) { // first call, store original label
			if (startmark != null & endmark != null) {
			    childNode.setLabel_(startmark+label+endmark); // use new marker
			    //System.out.format("New mark on \"%s\", new mark \"%s\"\n",label,startmark+label+endmark);
			    childNode.setAttribute("labelMarked",label);
			}
		    } else { // we have already stored original label
			if (startmark == null || endmark == null) { // reset to original label
			    //System.out.format("Resetting mark on \"%s\"\n",value);
			    childNode.setLabel_(value);
			    childNode.removeAttribute("labelMarked");
			} else { // new mark replaces old mark
			    //System.out.format("Found existing mark on \"%s\", new mark \"%s\"\n",value,startmark+label+endmark);
			    childNode.setLabel_(startmark+label+endmark);
			}
		    }
		} catch (NullPointerException e) {
		    System.out.format("Error setting label %s\n%s\n",childNode.identification, toString());
		    throw e;
		}
		hit=true;
		//} else {
		//System.out.format(" No match found %d %b.\n",level,childNode != null);
	    }
	}
	return hit;
    }
    /**
      * Constructs the resulting text based on the replacement rules for the current match.
      * This method will not influence the internal node structure.
      * 
      * @param replacementText
      *        The replacement rule following standard regex syntax ("$1" is group 1 etc.)
      */
    public String translate() { // returns processed replacement for current match
	String replacementText=this._replacement;
	return translate(replacementText);
    }
    public String translate(String replacementText) { // returns processed replacement for current match
	replacementText=replaceAnchorNames(replacementText);
	Plan plan=planReplacement(replacementText);
	return plan.getText();
    }

    /**
      * Replaces current match in current match with text based on the specified replacement rule. 
      * 
      * @param replacementText
      *        The replacement rule following standard regex syntax ("$1" is group 1 etc.)
      */
    public Plan replace() { // replace current match with processed replacement
	String replacementText=this._replacement;
	int group=this._group;
	return replace(replacementText, group);
    }
    public Plan replace(int group) { // replace current match with processed replacement
	String replacementText=this._replacement;
	return replace(replacementText, group);
    }
    public Plan replace(String replacementText, int group) { // replace current match with processed replacement
	replacementText=replaceAnchorNames(replacementText);
	Plan plan=planReplacement(replacementText,group); // make replacement plan
	//System.out.format("Plan %s\n",plan.toString());
	firstChild.shiftSiblingChainIndexes(plan);
	shiftMatchIndexes(plan);
	swap_(plan);
	return plan;
    }

    public Plan replace(String replacementText, int startIndex, int endIndex) { // replace index range with processed replacement
	replacementText=replaceAnchorNames(replacementText);
	Plan plan=planReplacement(replacementText,startIndex,endIndex); // make replacement plan
	System.out.format("Replace %s using Plan %s\n",toString(),plan.toString());
	firstChild.shiftSiblingChainIndexes(plan);
	shiftMatchIndexes(plan);
	swap_(plan);
	return plan;
    }

    /**
      * Replaces current match in current match with text based on the specified replacement rule. 
      * 
      * @param patternText
      *        The regular expression that it is searched for.
      * 
      * @param replacementText
      *        The replacement rule following standard regex syntax ("$1" is group 1 etc.)
      *        This is the text in the node, and this text will reappear if the node is unhidden.
      */
    public boolean replaceAll() { // replace current match with processed replacement
	String patternText=this._pattern;
	String replacementText=this._replacement;
	String[] path=this._path;
	return replaceAll(patternText,replacementText,path);
    }

    /**
      * Replaces current match in current match with text based on the specified replacement rule. 
      * 
      * @param patternText
      *        The regular expression that it is searched for.
      * 
      * @param replacementText
      *        The replacement rule following standard regex syntax ("$1" is group 1 etc.)
      *        This is the text in the node, and this text will reappear if the node is unhidden.
      *
      * @param node...
      *        node name stack for the node that should be processed 
      *        starting with the parent node in the node tree, 
      *        and ending with the name of the node that will be 
      *        processed ("..." is one or more nodes, "*" is any node, 
      *        "$" indicates top of the node tree).
      */
    public boolean replaceAll(String patternText, String replacementText) { // replace current match with processed replacement
	String[] path=this._path;
	return replaceAll(patternText, replacementText, path);
    }
    public boolean replaceAll(String patternText, String replacementText, String[] name1, String... name2) { // replace current match with processed replacement
	return replaceAll(patternText,replacementText,concat(name1,name2));
    }
    public boolean replaceAll(String patternText, String replacementText, String... path) { // replace current match with processed replacement
	patternText=replaceAnchorNames(patternText);
	//replacementText=replaceAnchorNames(replacementText);
	Character split=this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNodePattern(nodePatternText);
	return replaceAll_(this,patternText, replacementText, 0, allSubLevels,pattern,split); // replace current match with processed replacement
    }

    /**
      * Replaces current match in current match with text based on the specified replacement rule. 
      * 
      * @param patternText
      *        The regular expression that it is searched for.
      * 
      * @param replacementText
      *        The replacement rule following standard regex syntax ("$1" is group 1 etc.)
      *        This is the text in the node, and this text will reappear if the node is unhidden.
      * 
      * @param group
      *        The replacement rule following standard regex syntax ("$1" is group 1 etc.)
      * 
      * @param sublevel
      *        number of sublevels to process
      *        Only the node itself is processed when sublevel = 0.
      *        All sublevels are processed when sublevel = -1.
      *
      * @param node...
      *        node name stack for the node that should be processed 
      *        starting with the parent node in the node tree, 
      *        and ending with the name of the node that will be 
      *        processed ("..." is one or more nodes, "*" is any node, 
      *        "$" indicates top of the node tree).
      */
    public boolean replaceAll(int group, int subLevel) { // replace current match with processed replacement
	String patternText=this._pattern;
	String replacementText=this._replacement;
	String[] path=this._path;
	return replaceAll(patternText,replacementText,group,subLevel,path);
    }
    public boolean replaceAll(int group, int subLevel, String[] name1, String... name2) { // replace current match with processed replacement
	String patternText=this._pattern;
	String replacementText=this._replacement;
	return replaceAll(patternText,replacementText, group, subLevel, concat(name1,name2));
    }
    public boolean replaceAll(String patternText, String replacementText, int subLevel, String... path) { // replace current match with processed replacement
	return replaceAll(patternText,replacementText, 0, subLevel, path);
    }
    public boolean replaceAll(String patternText, String replacementText, int group, int subLevel, String[] name1, String... name2) { // replace current match with processed replacement
	return replaceAll(patternText,replacementText, group, subLevel, concat(name1,name2));
    }
    public boolean replaceAll(String patternText, String replacementText, int group, int subLevel, String... path) { // replace current match with processed replacement
	patternText=replaceAnchorNames(patternText);
	//replacementText=replaceAnchorNames(replacementText);
	Character split=this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNodePattern(nodePatternText);
	return replaceAll_(this,patternText, replacementText, group, subLevel, pattern, split); // replace current match with processed replacement
    }

    private RegexNode duplicate() {
	return duplicate(null);
    }
    private RegexNode duplicate(RegexNode parentNode) {
	RegexNode duplicateNode=new 
	    RegexNode(getText(),parentNode,parentNodeStartIndex,parentNodeEndIndex);
	duplicateNode.setNodeName(getNodeName());
	// copy groups
	duplicateNode.nMatchGroups=this.nMatchGroups;
	for(int ii=0;ii<=nMatchGroups;ii++) {
	    duplicateNode.matchStartIndexShifted.put(ii,this.matchStartIndexShifted.get(ii));
	    duplicateNode.matchEndIndexShifted.put(ii,this.matchEndIndexShifted.get(ii));
	    duplicateNode.matchStartIndexOriginal.put(ii,this.matchStartIndexOriginal.get(ii));
	    duplicateNode.matchEndIndexOriginal.put(ii,this.matchEndIndexOriginal.get(ii));
	    duplicateNode.matchGroups.put(ii,this.matchGroups.get(ii));
	}
	RegexNode child=firstChild.nextSibling;
	if (child!=lastChild) {
	    RegexNode duplicateChild = child.duplicate(duplicateNode);
	    duplicateNode.lastChild.prependChain(duplicateChild);
	    child=child.nextSibling;
	}
	duplicateNode.ignored=this.ignored;
	return duplicateNode;
    }
    
    public void copy(RegexNode blueprint) {
	// copy groups
	this.init(blueprint.getText());
	this.nMatchGroups=blueprint.nMatchGroups;
	for(int ii=0;ii<=nMatchGroups;ii++) {
	    this.matchStartIndexShifted.put(ii,blueprint.matchStartIndexShifted.get(ii));
	    this.matchEndIndexShifted.put(ii,blueprint.matchEndIndexShifted.get(ii));
	    this.matchStartIndexOriginal.put(ii,blueprint.matchStartIndexOriginal.get(ii));
	    this.matchEndIndexOriginal.put(ii,blueprint.matchEndIndexOriginal.get(ii));
	    this.matchGroups.put(ii,blueprint.matchGroups.get(ii));
	}
	RegexNode child=blueprint.firstChild.nextSibling;
	while (child!=blueprint.lastChild) {
	    RegexNode duplicateChild = child.duplicate(this);
	    this.lastChild.prependChain(duplicateChild);
	    child=child.nextSibling;
	}
	this.ignored=blueprint.ignored;
	this.setNodeName(blueprint.getNodeName());
    }
    
    /**
      * returns a copy of this node and including the sub node tree,
      **/
    public RegexNode duplicateOld() {
	// make duplicate, and initialise it...
	if (parentNode != null) System.out.format("Parent: %s\n",parentNode.identification);
	System.out.format("Duplicating: %s\n%s\n",identification,toString());
	RegexNode twin = new RegexNode(getText(),
					   parentNode,parentNodeStartIndex,parentNodeEndIndex);
	// copy node name
	twin.setNodeName(getNodeName());
	// copy groups
	twin.nMatchGroups=this.nMatchGroups;
	for(int ii=0;ii<=nMatchGroups;ii++) {
	    twin.matchStartIndexShifted.put(ii,this.matchStartIndexShifted.get(ii));
	    twin.matchEndIndexShifted.put(ii,this.matchEndIndexShifted.get(ii));
	    twin.matchStartIndexOriginal.put(ii,this.matchStartIndexOriginal.get(ii));
	    twin.matchEndIndexOriginal.put(ii,this.matchEndIndexOriginal.get(ii));
	    twin.matchGroups.put(ii,this.matchGroups.get(ii));
	}
	// copy ignored status
	twin.ignored=this.ignored;
	// add children
	RegexNode child=firstChild.nextSibling;
	while (child != lastChild) { // last child is not a valid child
	    RegexNode twinChild=child.duplicateOld();
	    twinChild.parentNode=twin;
	    twinChild.parentNodeStartIndex=child.parentNodeStartIndex;
	    twinChild.parentNodeEndIndex=child.parentNodeStartIndex;
	    twin.lastChild.prependChain(twinChild);
	    child=child.nextSibling;
	}
	return twin;
    }
    //
    //******************** H I D E   A N D   U N H I D E ******************
    //
    /**
      * Hides text that has not been nodeged so far.
      *
      * @param node
      *        Name of node name given to the hidden text.
      *         
      */
    public RegexNode hideTheRest() { // hide regions that are unnodeged
	hideTheRest(this._node,this._label);
	return this;
    }
    public RegexNode hideTheRest(String node) { // hide regions that are unnodeged
	hideTheRest(node,this._label);
	return this;
    }
    /**
      * Hides text that has not been nodeged so far, and replaces it with a label.
      *
      * @param node
      *        Name of node name given to the hidden text.
      *         
      * @param label
      *        Label put in place of the hidden text.
      *         
      */
    public boolean hideTheRest(String node, String label) { // node regions that are unnodeged/ignored
	label=replaceAnchorNames(label);
	boolean hit=false;
	RegexNode lastOk=firstChild;
	RegexNode child=firstChild.nextSibling;
	while(child != lastChild) {
	    //if  (! child.ignored){
		if (hide_(node,label,lastOk,child) != null) {
		    hit=true;
		}
		lastOk=child;
	    //}
	    child=child.nextSibling;    // point to next valid element in chain
	}
	if (hide_(node,label,lastOk,lastChild)!=null) {
		    hit=true;
	}
	return hit;
    }
    public RegexNode hideTheRestAll() {
	this.hideTheRestAny();
	return this;
    }
    public RegexNode hideTheRestAll(String node) {
	this.hideTheRestAny(node);
	return this;
    }
    public RegexNode hideTheRestAll(String node,String label) {
	this.hideTheRestAny(node,label);
	return this;
    }
    public RegexNode hideTheRestAll(String[] path) {
	this.hideTheRestAny(path);
	return this;
    }
    public RegexNode hideTheRestAll(String node, String label, String[] name1, String... name2) {
	this.hideTheRestAny(node,label,name1,name2);
	return this;
    }
    public RegexNode hideTheRestAll(String node, String label, String... path) {
	this.hideTheRestAny(node,label,path);
	return this;
    }
    public boolean hideTheRestAny() {
	String node=this._node;
	String label=this._label;
	String[] path=this._path;
	return hideTheRestAny(node,label,path);
    }
    public boolean hideTheRestAny(String node) {
	String label=this._label;
	String[] path=this._path;
	return hideTheRestAny(node,label,path);
    }
    public boolean hideTheRestAny(String node,String label) {
	String[] path=this._path;
	return hideTheRestAny(node,label,path);
    }
    public boolean hideTheRestAny(String[] path) {
	String node=this._node;
	String label=this._label;
	return hideTheRestAny(node,label,path);
    }
    public boolean hideTheRestAny(String node, String label, String[] name1, String... name2) {
	return hideTheRestAny(node, label, concat(name1,name2));
    }
    public boolean hideTheRestAny(String node, String label, String... path) {
	Character split=this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNodePattern(nodePatternText);
	return  hideTheRestAny_(this,node, label, pattern,split);
    }
    /**
      * Searches for nodes with the specified name, and hides them giving them
      * the specified node name.
      *
      * @param newNode
      *        Name of the nodes that are made.
      *         
      * @param node...
      *        node name stack for the node that should be processed 
      *        starting with the parent node in the node tree, 
      *        and ending with the name of the node that will be 
      *        processed ("..." is one or more nodes, "*" is any node, 
      *        "$" indicates top of the node tree).
      */
    public boolean hideNode() {
	String newNode=this._node;
	String label=this._label;
	String[] path=this._path;
	return hideNode(newNode, label, path);
    }
    public boolean hideNode(String newNode) {
	String label=this._label;
	String[] path=this._path;
	return hideNode(newNode, label, path);
    }
    public boolean hideNode(String newNode,String label) {
	String[] path=this._path;
	return hideNode(newNode, label, path);
    }
    public boolean hideNode(String newNode, String label, String[] name1, String... name2) {
	return hideNode(newNode, label, concat(name1,name2));
    }
    public boolean hideNode(String newNode, String label, String... path) {
	label=replaceAnchorNames(label);
	boolean found=false;
	Integer sid=maxsid++;
	Character split = this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNewPattern(nodePatternText);
	RegexNode nodeNode=getNode_(this,sid,pattern,split);
	while (nodeNode != null) {
	    nodeNode.hide_(newNode,label,0);
	    found=true;
	    nodeNode=getNode_(this,sid,pattern,split);
	}
	return found;
    }
    /**
      * Searches for nodes with the specified name, and hides the specified group giving it
      * the specified node name.
      *
      * @param groupNode
      *        Name of the nodes that are made from the group.
      *        
      * @param group
      *        Group that will be hidden
      *         
      * @param node...
      *        node name stack for the node that should be processed 
      *        starting with the parent node in the node tree, 
      *        and ending with the name of the node that will be 
      *        processed ("..." is one or more nodes, "*" is any node, 
      *        "$" indicates top of the node tree).
      */
    public boolean hideNodeGroup() {
	String groupNode=this._node;
	String label=this._label;
	int group=this._group;
	String[] path=this._path;
	return hideNodeGroup(groupNode, label, group, path);
    };
    public boolean hideNodeGroup(String groupNode) {
	String label=this._label;
	int group=this._group;
	String[] path=this._path;
	return hideNodeGroup(groupNode, label, group, path);
    }
    public boolean hideNodeGroup(int group) {
	String groupNode=this._node;
	String label=this._label;
	String[] path=this._path;
	return hideNodeGroup(groupNode, label, group, path);
    };
    public boolean hideNodeGroup(String groupNode, int group) {
	String label=this._label;
	String[] path=this._path;
	return hideNodeGroup(groupNode, label, group, path);
    }
    public boolean hideNodeGroup(String groupNode, String label, int group) {
	String[] path=this._path;
	return hideNodeGroup(groupNode, label, group, path);
    }
    public boolean hideNodeGroup(int group, String[] name1,String... name2) {
	String groupNode=this._node;
	String label=this._label;
	return hideNodeGroup(groupNode, label, group, concat(name1,name2));
    }
    public boolean hideNodeGroup(String groupNode, int group, String[] name1,String... name2) {
	String label=this._label;
	return hideNodeGroup(groupNode, label, group, concat(name1,name2));
    }
    public boolean hideNodeGroup(String groupNode, String label, int group, String[] name1,String... name2) {
	return hideNodeGroup(groupNode, label, group, concat(name1,name2));
    }
    public boolean hideNodeGroup(String groupNode, String label, int group, String... path) {
	label=replaceAnchorNames(label);
	boolean found=false;
	Integer sid=maxsid++;
	Character split = this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNewPattern(nodePatternText);
	RegexNode nodeNode=getNode_(this,sid,pattern,split);
	while (nodeNode != null) {
	    if (debug) System.out.format("***********Found node: %s",nodeNode.getIdentification());
	    nodeNode.hide_(groupNode,label,group);
	    found=true;
	    nodeNode=getNode_(this,sid,pattern,split);
	}
	return found;
    }
    // make parent
    // returns parent
    public boolean makeParentAll() {
	String node=this._node;
	String label=this._label;
	String[] path=this._path;
	return makeParentAll(node, label, path);
    }
    public boolean makeParentAll(String node) {
	String label=this._label;
	String[] path=this._path;
	return makeParentAll(node, label, path);
    }
    public boolean makeParentAll(String node,String label) {
	String[] path=this._path;
	return makeParentAll(node, label, path);
    }
    public boolean makeParentAll(String node, String label, String[] name1, String... name2) {
	return makeParentAll(node, label, concat(name1, name2));
    }
    public boolean makeParentAll(String node, String label, String... path) {
	Character split=this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNodePattern(nodePatternText);
	return makeParentAll_(node, label, this, 0, pattern, split);
    }
    private boolean makeParentAll_(String node, String label, RegexNode root, Integer targetlevel, Pattern pattern, Character split) {
	boolean hit=false;
	RegexNode child=firstChild.nextSibling;
	while (child!= lastChild & !ignored) { // last child is not a valid child
	    child.marked=false;
	    if (! child.ignored) {
		if (child.makeParentAll_(node,label,root,targetlevel,pattern,split)) {
		    hit=true;
		};
	    };
	    boolean match = (! child.ignored);
            Integer imatch=child.nodeMatch(root,pattern,split);
            if (match & pattern !=null) match=imatch != null;
 	    if (match) {
		if (debug) System.out.format("Found match: %s\n",child.getIdentification());
		// mark parent
		RegexNode childNode=child;
		int level =0;
		Integer tlevel = targetlevel;
		if (targetlevel < 0) {
		    tlevel = imatch + 1 + targetlevel; // count from top, -1 being top level in node match
		}
		while (level < tlevel & childNode != null & childNode != root){
		    level=level+1;
		    childNode=childNode.parentNode;
		}
		if (level == tlevel & childNode != null) {
		    if (debug) System.out.format("Marked for unhide: %s\n",childNode.getIdentification());
		    childNode.marked=true;
		}
	    }
	    if (child.marked) { // delayed processing...
		if (debug) System.out.format("Unhiding %s\n",child.getIdentification());
		try {
		    RegexNode nextChild=child.nextSibling;
                    child.makeParent(node,label);
		    child=nextChild;
                } catch (NullPointerException e) {
                    System.out.format("Error unhiding %s\n%s\n",child.identification, toString());
                    throw e;
                }
		hit=true;
	    } else {
		child=child.nextSibling;
	    }
	}
	return hit;
    }
    public RegexNode makeParent() {
	String node=this._node;
	String label=this._label;
	return makeParent(node,label);
    }
    public RegexNode makeParent(String node) {
	String label=this._label;
	return makeParent(node,label);
    }
    public RegexNode makeParent(String node,String label) {
	if (parentNode != null) {
	    String lab=this.getLabel();
	    RegexNode p = parentNode.hide_(node,label,this.parentNodeStartIndex,this.parentNodeEndIndex,null);
	    this.setLabel(lab);
	    return p;
	} else {
	    RegexNode p =  new RegexNode(node+":@;",':',';','@',this);
	    this.setLabel(label);
	    return p;
	}
    }

    /**
      * Hides the current match giving it a node. 
      * The hidden text is removed from the search text. 
      *        
      * @param node
      *        The name of the node given to the hidden text.
      */
    public RegexNode hide() {
	String node=this._node;
	String label=this._label;
	int group=this._group;
	return hide_(node,label,group);
    }
    public RegexNode hide(int group) {
	String node=this._node;
	String label=this._label;
	return hide_(node,label,group);
    }
    public RegexNode hide(String node) {
	String label=this._label;
	int group=this._group;
	return hide_(node,label,group);
    }
    /**
      * Hides the current match giving it a node. 
      * The hidden text is removed from the search text. 
      *        
      * @param node
      *        The name of the node given to the hidden text.
      *
      * @param group
      *        The group that should be hidden (0=entire match).
      */
    public void hide(String node, int group) {
	String label=this._label;
	hide_(node,label,group);
    }

    /**
      * Hides the current match giving it a node. 
      * The hidden text is replaced by a label in the search text. 
      *        
      * @param node
      *        The name of the node given to the hidden text.
      *
      * @param label
      *        label to replace the hidden text in the search text.
      */
    public RegexNode hide(String node, String label) { // hide current match 
	int group=this._group;
	label=replaceAnchorNames(label);
	return hide_(node,label,group);
    }
    
    /**
      * Hides the current match giving it a node. 
      * The hidden text is replaced by a label in the search text. 
      *        
      * @param node
      *        The name of the node given to the hidden text.
      *
      * @param label
      *        label to replace the hidden text in the search text.
      *
      * @param group
      *        The group that should be hidden (0=entire match).
      */
    public RegexNode hide(String node, String label, int group) { // hide current match 
	label=replaceAnchorNames(label);
	return hide_(node,label,group);
    }
    public RegexNode hide(String node, String label, int startIndex, int endIndex, RegexNode child) { // hide current match 
	label=replaceAnchorNames(label);
	return hide_(node,label, startIndex, endIndex, child);
    }
    /**
      * repeats an earlier hideAll command.
      *
      * @param node
      *        The name of the node given to the hidden text.
      */
    /**
      * Hides all occurences of the specified pattern, giving them the same node name.
      * Only the node invoking the method is searched.
      * 
      * @param node
      *        The name of the node given to the hidden text.
      *
     * @param patternText
      *        The regular expression that it is searched for.
      *        
      * @param label
      *        label to replace the hidden text in the search text.
     *        
     * @param sublevel
     *        number of sublevels to process
     *        Only the node itself is processed when sublevel = 0.
     *        All sublevels are processed when sublevel = -1.
      *
      * @param node...
      *        node name stack for the node that should be processed 
      *        starting with the parent node in the node tree, 
      *        and ending with the name of the node that will be 
      *        processed ("..." is one or more nodes, "*" is any node, 
      *        "$" indicates top of the node tree).
      */
    public RegexNode hideAll() { // hide current match 
	String node=this._node;
	String patternText=this._pattern;
	String label=this._label;
	String[] path=this._path;
	return hideAll(node, patternText, label, path);
    };
    public RegexNode hideAll(String patternText) { // hide current match 
	String node=this._node;
	String label=this._label;
	String[] path=this._path;
	return hideAll(node, patternText, label, path);
    };
    public RegexNode hideAll(String node,String patternText) { // hide current match 
	String label=this._label;
	String[] path=this._path;
	return hideAll(node, patternText, label, path);
    };
    public RegexNode hideAll(String node,String patternText,String label) { // hide current match 
	String[] path=this._path;
	return hideAll(node, patternText, label, path);
    };
    public RegexNode hideAll(String node, String patternText, String label, 
			   String[] name1, String... name2) { // hide current match 
	return hideAll(node, patternText, label, concat(name1,name2));
    };
    public RegexNode hideAll(String node, String patternText, String label, 
			   String... path) { // hide current match 
	hideAny(node,patternText,label,path);
	return this;
    };
    public boolean hideAny(String patternText) { // hide current match 
	String node=this._node;
	String label=this._label;
	String[] path=this._path;
	return hideAny(node, patternText, label, path);
    };
    public boolean hideAny(String node, String patternText) { // hide current match 
	String label=this._label;
	String[] path=this._path;
	return hideAny(node, patternText, label, path);
    };
    public boolean hideAny(String node, String patternText, String label) { // hide current match 
	String[] path=this._path;
	return hideAny(node, patternText, label, path);
    };
    public boolean hideAny(String node, String patternText, String label, 
			   String[] name1, String... name2) { // hide current match 
	return hideAny(node, patternText, label, concat(name1,name2));
    };
    public boolean hideAny(String node, String patternText, String label, 
			   String... path) { // hide current match 
	patternText=replaceAnchorNames(patternText);
	label=replaceAnchorNames(label);
	Character split=this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNodePattern(nodePatternText);
	return hideAny_(this,0,node,patternText,label,pattern,split);
    }
    /**
     * Unhides all children of the calling node.
     * 
     * @return the next node in the sibling chain.
      *
      * @param node...
      *        node name stack for the node that should be processed 
      *        starting with the parent node in the node tree, 
      *        and ending with the name of the node that will be 
      *        processed ("..." is one or more nodes, "*" is any node, 
      *        "$" indicates top of the node tree).
     * 
     */
    public RegexNode unhide() {
	String[] path=this._path;
	return unhide(path);
    }
    public RegexNode unhide(String[] name1, String... name2) {
	return unhide(concat(name1,name2));
    }
    public RegexNode unhide(String... path) {
	Character split = this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNewPattern(nodePatternText);
	return unhide_(this,pattern,split);
    }
    /**
     * Unhides all occurences of specified node.
      *
      * @param node...
      *        node name stack for the node that should be processed 
      *        starting with the parent node in the node tree, 
      *        and ending with the name of the node that will be 
      *        processed ("..." is one or more nodes, "*" is any node, 
      *        "$" indicates top of the node tree).
     */
    public RegexNode unhideAll() {
	this.unhideAny();
	return this;
    };
    public RegexNode unhideAll(String[] name1, String... name2) {
	this.unhideAny(name1,name2);
	return this;
    };
    public RegexNode unhideAll(String... path) {
	this.unhideAny(path);
	return this;
    };
    public RegexNode unhideAll(Integer level) {
	this.unhideAny(level);
	return this;
    }
    public RegexNode unhideAll(Integer level, String[] name1, String... name2) {
	this.unhideAny(level,name1,name2);
	return this;
    }
    public RegexNode unhideAll(Integer level, String... path) {
	this.unhideAny(level,path);
	return this;
    }
    /**
     * Unhides all occurences of specified parent node.
      *
      * @param level
      *        The parent level that should be unhidden (0= lowest level)
      *
      * @param node...
      *        node name stack for the node that should be processed 
      *        starting with the parent node in the node tree, 
      *        and ending with the name of the node that will be 
      *        processed ("..." is one or more nodes, "*" is any node, 
      *        "$" indicates top of the node tree).
     */
    public boolean unhideAny() {
	String[] path=this._path;
	return  unhideAny(path);
    }
    public boolean unhideAny(String[] name1, String... name2) {
	return  unhideAny(concat(name1,name2));
    }
    public boolean unhideAny(String... path) {
	Character split = this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNewPattern(nodePatternText);
	return unhideAny_(this,0,pattern,split);
    }
    public boolean unhideAny(Integer level) {
	String[] path=this._path;
	return  unhideAny(level,path);
    }
    public boolean unhideAny(Integer level, String[] name1, String... name2) {
	return  unhideAny(level,concat(name1,name2));
    }
    public boolean unhideAny(Integer level, String... path) {
	Character split = this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNewPattern(nodePatternText);
	return unhideAny_(this,level,pattern,split);
    }
    //******************** F O L D **********************
    public boolean isunfolded() { // is this node unfolded?
	return (startFoldNode != null || endFoldNode!= null);
    }
    public void unfold(String slabel,String elabel) { // unfold this node 
	slabel=replaceAnchorNames(slabel);
	elabel=replaceAnchorNames(elabel);
	unfold_(slabel,elabel);
    }
    public boolean unfoldAll(String slabel,String elabel) {
	String[] path=this._path;
	return unfoldAll(slabel,elabel,path);
    }
    public boolean unfoldAll(String slabel,String elabel,String... path) {
	slabel=replaceAnchorNames(slabel);
	elabel=replaceAnchorNames(elabel);
	Character split = this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNewPattern(nodePatternText);
	return unfoldAll_(slabel,elabel,0,this,pattern,split);
    }
    public boolean unfoldParentAll(String slabel,String elabel) {
	String[] path=this._path;
	return unfoldParentAll(slabel,elabel,path);
    };
    public boolean unfoldParentAll(String slabel,String elabel,String... path) {
	slabel=replaceAnchorNames(slabel);
	elabel=replaceAnchorNames(elabel);
	Character split = this._s;
	String nodePatternText=getPatternText(path,split);
	if (debug) System.out.format("Pattern: \"%s\"\n",nodePatternText);
	Pattern pattern=getNewPattern(nodePatternText);
	return unfoldAll_(slabel,elabel,-1,this,pattern,split);
    }
    public boolean unfoldParentAll(ArrayList<RegexNode>  nodeList, String slabel, String elabel, String[] name1, String... name2) {
	return unfoldParentAll(nodeList, slabel, elabel, concat(name1, name2));
    }
    public boolean unfoldParentAll(ArrayList<RegexNode>  nodeList, String slabel, String elabel, String... path) {
	slabel=replaceAnchorNames(slabel);
	elabel=replaceAnchorNames(elabel);
	Character split=this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNodePattern(nodePatternText);
	return unfoldParentAll_(nodeList, slabel, elabel, this, pattern, split);
    }
    private boolean unfoldParentAll_(ArrayList<RegexNode>  nodeList, String slabel, String elabel, 
				  RegexNode root, Pattern pattern, Character split) {
	if (nodeList == null) return false;
	boolean hit=false;
	for (RegexNode child : nodeList) {
	    while (child.unfoldParent_(slabel,elabel,-1, root, pattern, split)) {
		hit=true;
	    }
	}
	return hit;
    }
    public boolean unfoldParentAll(RegexNode  cNode, String slabel, String elabel) {
	String[] path=this._path;
	return unfoldParentAll(cNode,slabel,elabel,path);
    }
    public boolean unfoldParentAll(RegexNode  cNode, String slabel, String elabel, String[] name1, String... name2) {
	return unfoldParentAll(cNode, slabel, elabel, concat(name1, name2));
    }
    public boolean unfoldParentAll(RegexNode  cNode, String slabel, String elabel, String... path) {
	slabel=replaceAnchorNames(slabel);
	elabel=replaceAnchorNames(elabel);
	Character split=this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNodePattern(nodePatternText);
	return unfoldParentAll_(cNode, slabel, elabel, this, pattern, split);
    }
    private boolean unfoldParentAll_(RegexNode  cNode, String slabel, String elabel, 
				  RegexNode root, Pattern pattern, Character split) {
	boolean hit=false;
	while (cNode.unfoldParent_(slabel,elabel,-1, root, pattern, split)) {
	    hit=true;
	}
	return hit;
    }
    public boolean unfoldParent(RegexNode child, String slabel, String elabel) {
	String[] path=this._path;
	return unfoldParent(child,slabel,elabel,path);
    }
    public boolean unfoldParent(RegexNode child, String slabel, String elabel, String... path) {
	return unfoldParent(child, slabel, elabel, -1, path);
    }
    public boolean unfoldParent(RegexNode child, String slabel, String elabel, int targetlevel, String... path) {
	slabel=replaceAnchorNames(slabel);
	elabel=replaceAnchorNames(elabel);
	Character split = this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNewPattern(nodePatternText);
	return child.unfoldParent_(slabel, elabel, targetlevel, this, pattern, split);
    }
    public void fold() { // fold this node 
	String label=this._label;
	fold(label);
    }
    public void fold(String label) { // fold this node 
	if (label != null) label=replaceAnchorNames(label);
	fold_(label);
    }
    public boolean foldAll() {
	return foldAll_((String) null,this,(Pattern) null ,(Character) null);
    }
    public boolean foldAll(String label,String... path) {
	label=replaceAnchorNames(label);
	Character split = this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNewPattern(nodePatternText);
	return foldAll_(label,this,pattern,split);
    }
    public boolean setLabelUnPaired(String slabel, String elabel, String[] name1, String... name2) {
	return setLabelUnPaired(slabel, elabel, concat(name1, name2));
    }
    public boolean setLabelUnPaired(String slabel, String elabel, String... path) {
	slabel=replaceAnchorNames(slabel);
	elabel=replaceAnchorNames(elabel);
	Character split=this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNodePattern(nodePatternText);
	return setLabelUnPaired_(slabel,elabel,this,0,pattern,split);
    }

    public boolean setLabelUnPaired(String slabel, String elabel, int targetlevel, String[] name1, String... name2) {
	return setLabelUnPaired(slabel, elabel, targetlevel, concat(name1, name2));
    }
    public boolean setLabelUnPaired(String slabel, String elabel, int targetlevel, String... path) {
	slabel=replaceAnchorNames(slabel);
	elabel=replaceAnchorNames(elabel);
	Character split=this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNodePattern(nodePatternText);
	return setLabelUnPaired_(slabel,elabel,this,targetlevel,pattern,split);
    }
    private boolean setLabelUnPaired_(String slabel,String elabel,
				      RegexNode root, int targetlevel, Pattern pattern, Character split) { // hide current match 
	boolean hit=false;
	Integer sid=maxsid++;
	RegexNode nodeNode=getNode_(root,sid,pattern,split);
	while (nodeNode != null) {
	    if (nodeNode.endFoldNode != null) {
		nodeNode.setLabel(slabel);
		nodeNode.endFoldNode.setLabel(elabel);
		hit=true;
	    }
	    nodeNode=getNode_(root,sid,pattern,split);
	}
	return hit;
    }
    public boolean foldPaired() {
	return foldPaired_((String) null,this,(Pattern) null ,(Character) null);
    }
    public boolean foldPaired(String label,String... path) {
	label=replaceAnchorNames(label);
	Character split = this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNewPattern(nodePatternText);
	return foldPaired_(label,this,pattern,split);
    }
    public RegexNode addUnFolded(RegexNode startNode, RegexNode endNode) {
	String nodeName=this._node;
	String startLabel=this._label;
	return addUnFolded(nodeName,startLabel,startLabel,startLabel,startNode,endNode);
    };
    public RegexNode addUnFolded(String nodeName, String startLabel,
			       RegexNode startNode, RegexNode endNode) {
	return addUnFolded(nodeName,startLabel,startLabel,startLabel,startNode,endNode);
    }
    public RegexNode addUnFolded(String nodeName, String startLabel, String endLabel,
			       RegexNode startNode, RegexNode endNode) {
	return addUnFolded(nodeName, startLabel, startLabel, endLabel,startNode, endNode);
    }
    public RegexNode addUnFolded(String nodeName, String label, String startLabel, String endLabel,
			       RegexNode startNode, RegexNode endNode) {
	if (startNode.parentNode != endNode.parentNode || startNode.parentNode ==null || endNode.parentNode == null) {
	    if (debug) {
		System.out.format("addUnfolded parent mismatch  (%s#%s->",startNode.getIdentification(),startNode.getNodeName());
		if (startNode.parentNode != null) System.out.format("%s#%s",startNode.parentNode.getIdentification(),startNode.parentNode.getNodeName());
		System.out.format(", %s#%s->",endNode.getIdentification(),endNode.getNodeName());
		if (endNode.parentNode != null) System.out.format("%s#%s",endNode.parentNode.getIdentification(),endNode.parentNode.getNodeName());
		System.out.format(")\n");
	    }
	}
	RegexNode s=new RegexNode (startNode.parentNode,startNode.getParentStartIndex(),startNode.getParentStartIndex());
	RegexNode e=new RegexNode (endNode.parentNode,endNode.getParentEndIndex(),endNode.getParentEndIndex());
	s.setAttribute("labelFolded",label);
	s.setNodeName(nodeName);
	e.setNodeName(nodeName+"_");
	e.setAttribute("matches",s.getIdentification());
	s.endFoldNode=e;
	e.startFoldNode=s;
	startNode.prependSibling_(s,startLabel);
	endNode.appendSibling_(e,endLabel);
	return s;
    }
    public RegexNode getEndFoldNode() {
	return endFoldNode;
    }
    public RegexNode getStartFoldNode() {
	return startFoldNode;
    }
    //
    //******************** I G N O R E ******************
    //

    /**
      * "ignores" a node.
      * "Ignored" nodes are ignored by {@link #unhide}, {@link #hideAll}, {@link #unhideAll}  etc.
      */
    public void ignore() { // ignored nodes are not "un-hidden" by "unhide"
	ignored=true;
    }

    /**
     * "ignores"/"unignores" all nodes with specified node name recursively so that they are ignored/processed by {@link #unhide} etc.
      * Note that the Root node is never "ignored".
     * 
     * @param node
     *        node name of the nodes that should be ignored/unignored.
     * 
     * @param ignore
     *        true if node should be ignored, false if it should be unignored
     */
    /**


    /**
      * "Ignores" all child nodes at and below envoking node.
      * Note that the Root node is never "ignored".
      * "Ignored" nodes are ignored by {@link #unhide} etc.
      */
    public RegexNode ignoreAll() { // ignored nodes are not "un-hidden" by "unhide"
	if (parentNode != null) ignore(); // do not ignore root node
	RegexNode child=firstChild.nextSibling;
	while(child != lastChild) {
	    child.ignoreAll();
	    child=child.nextSibling;    // point to next valid element in chain
	}
	return this;
    }
    /*
     * "Ignores" all nodes with specified node name recursively so that they are ignored by {@link #unhide} etc.
      * Note that the Root node is never "ignored".
     * 
     * @param node
     *        node name of the nodes that should be ignored.
     */
    public RegexNode ignoreAll(String[] name1, String... name2) { // ignored nodes are not "un-hidden" by "unhide"
	return ignoreAll(concat(name1,name2));
    }
    public RegexNode ignoreAll(String... path) { // ignored nodes are not "un-hidden" by "unhide"
	Character split = this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNewPattern(nodePatternText);
	return ignoreAll_(this,pattern,split);
    }
    /**
      * "un-ignores" a node.
      * "Ignored" nodes are ignored by {@link #unhide} etc.
      */
    public void unignore() { // ignored nodes are not "un-hidden" by "unhide"
	ignored=false;
    }

    /**
      * "Un-ignores" all nodes with specified node name recursively so that they are not ignored by {@link #unhide} etc.
      * 
      * @param node
      *        node name of the nodes that should be ignored.
      */
    public RegexNode unignoreAll() {
	String[] path = this._path;
	return unignoreAll(path);
	//unignore();
	//RegexNode child=firstChild.nextSibling;
	//while(child != lastChild) {
	 //   child.unignoreAll();
	 //   child=child.nextSibling;    // point to next valid element in chain
	//}
	//return this;
    }
    public RegexNode unignoreAll(String[] name1,String... name2) {
	return unignoreAll(concat(name1,name2));
    }
    public RegexNode unignoreAll(String... path) {
	Character split = this._s;
	String nodePatternText=getPatternText(path,split);
	Pattern pattern=getNewPattern(nodePatternText);
	return unignoreAll_(this,pattern,split);
    }
    /**
      * Retrieves ignore-flag for specified node.
      * Throws error if nodes with same name has different ignore status.
      * 
      * @param node
      *        node name of the nodes for which the ignore-flag should be retrieved.
      */
    public boolean getIgnore(String node) { // ignored nodes are not "un-hidden" by "unhide"
	Boolean ignore=null;
	Boolean ret= getIgnore(node,ignore);
	if (ret==null) { // node is not present
	    // System.out.format("Warning; Unable to get \"ignore\"-status for missing node \"%s\"\n%s\n",node,toString());
	    ret=false;
	}
	return ret;
    }
    /**
     * Sets the node-location for all child-nodes recursively.
     */
    public void setLocationAll() {
	setLocationAll_(null,0,0);
    }
    public Boolean afterLocation(RegexNode target) {
	Integer targetPosition =target.getLocationPosition();
	if (targetPosition!=null & locationPosition != null) {
	    return targetPosition < locationPosition;
	} else {
	    return null;
	}
    }
    public Boolean beforeLocation(RegexNode target) {
	Integer targetPosition =target.getLocationPosition();
	if (targetPosition!=null & locationPosition != null) {
	    return targetPosition > locationPosition;
	} else {
	    return null;
	}
    }
    public Integer matchesLocationLevel(RegexNode target) {
	Integer targetLocationLevel =target.locationLevel;;
	Integer[] targetLocation =target.getLocation();
	if (location == null || targetLocation == null) return null;
	Integer maxLevel=Math.max(targetLocationLevel,locationLevel);
	Integer level=null;
	boolean mismatch=false;
	for (int ii=0;ii<maxLevel & ! mismatch;ii++) {
	    if (targetLocation[ii]==location[ii]) {
		level=ii;
	    } else {
		mismatch=true;
	    }
	}
	return level;
    }
    /**
     * Returns number of the children belonging to this node 
     * last time the method {@link #setLocation} was run.
     *
     * @return number of children.
      */
    public Integer countChildren() {
	Integer ret=0;
	RegexNode child=firstChild.nextSibling;
	while (child != lastChild) { // last child is not a valid child
	    ret++;
	    child=child.nextSibling;
	}
	return ret;
    }
    public Boolean isBefore(RegexNode sibling) {
	if (sibling == this) return false;
	Boolean ret=null;
	if (sibling.getParent() != getParent()) return ret;
	if (parentNodeStartIndex < sibling.parentNodeStartIndex) {
	    ret=true;
	} else if (parentNodeStartIndex > sibling.parentNodeStartIndex) {
	    ret=false;
	} else {
	    ret=false;
	    RegexNode node=this.getNextSibling();
	    while(ret==null & node != parentNode.getLastChild()) {
		if (node == sibling) {
		    ret=true;
		} else {
		    node=node.getNextSibling();
		}
	    }
	}
	return ret;
    }
    public Boolean isAfter(RegexNode sibling) {
	if (sibling == this) return false;
	Boolean ret=null;
	if (sibling.getParent() != getParent()) return ret;
	if (parentNodeStartIndex < sibling.parentNodeStartIndex) {
	    ret=false;
	} else if (parentNodeStartIndex > sibling.parentNodeStartIndex) {
	    ret=true;
	} else {
	    ret=false;
	    RegexNode node=this.getPrevSibling();
	    while(ret==null & node != parentNode.getFirstChild()) {
		if (node == sibling) {
		    ret=true;
		} else {
		    node=node.getPrevSibling();
		}
	    }
	}
	return ret;
    }
    public boolean hasChildren() {
	return (firstChild.nextSibling != lastChild);
    }
    public boolean hasStructure() {
	String[] path=this._path;
	return hasStructure(path);
    };
    public boolean hasStructure(String... path) {
	boolean ret=true;
	int pos=0;
	RegexNode child=firstChild.nextSibling;
	while (child != lastChild) { // last child is not a valid child
	    if (path == null) {
		ret=false;
	    } else if (path.length -1 <  pos) {
		ret=false;
	    } else if (! child.getNodeName().equals(path[pos])) {
		ret=false;
	    }
	    pos++;
	    child=child.nextSibling;
	}
	return ret;
    }
    /**
     * Returns location data for the node.
     * The location is the position in the node tree.
     *
     * @return The location of the node in the node tree.
     *         First element is the location in the parent node.
      *         Next is the parents location in its parent node etc.
      *         If only one node is present, the location is zero.
      */
    public Integer[] getLocation() {
	return location;
    }
    public Integer getLocationPosition() {
	return locationPosition;
    }
    /**
     * Sets an attribute object.
     *
     * @param attName
     *        The name of the attribute.
     *
     * @param attObject
     *        The attribute object.
     */
    public int setAttributeAll(Object attObject) {
	String attName=this._att;
	String[] path = this._path;
	return setAttributeAll(attName,attObject,path);
    }
    public int setAttributeAll(String attName, Object attObject) {
	String[] path = this._path;
	return setAttributeAll(attName,attObject,path);
    }
    public int setAttributeAll(String attName, Object attObject, String[] name1, String... name2) {
	return setAttributeAll(attName,attObject,concat(name1,name2));
    }
    public int setAttributeAll(String attName, Object attObject, String... path) {
	int cnt=0;
	if (path.length!=0) {
	    RegexNode node = getNode(path);
	    while (node !=null) {
		cnt++;
		node.attributes.put(attName,attObject);
		node = getNode(path);
	    }
	} else {
	    cnt++;
	    attributes.put(attName,attObject);
	}
	return cnt;
    }
    public int addAttributeAll(String attName, Object attObject, String... path) {
	int cnt=0;
	RegexNode node = getNode(path);
	while (node !=null) {
	    cnt++;
	    node.addAttribute(attName,attObject);
	    node = getNode(path);
	}
	return cnt;
    }
    public void addAttribute(String attName, Object attObject) {
	@SuppressWarnings("unchecked")
	    ArrayList<Object> list = (ArrayList<Object>) getAttribute(attName);
	if (list == null ) {
	    list = new ArrayList<Object>();
	    setAttribute(attName,list);
	}
	if (! list.contains(attObject)) list.add(attObject);
    }
    public int countAttribute(String attName) {
	@SuppressWarnings("unchecked")
	    ArrayList<Object> list = (ArrayList<Object>) getAttribute(attName);
	int cnt=0;
	if (list != null) cnt=list.size();
	return cnt;
    }
    public void setAttribute(String attName, Object attObject) {
	attributes.put(attName,attObject);
    }
    public Object removeAttribute(String attName) {
	return attributes.remove(attName);
    }
    public int removeAttributeAll() {
	String attName=this._att;
	String[] path=this._path;
	return removeAttributeAll(attName,path);
    }
    public int removeAttributeAll(String attName) {
	String[] path=this._path;
	return removeAttributeAll(attName,path);
    }
    public int removeAttributeAll(String attName, String... path) {
	int cnt=0;
	if (path.length!=0) {
	    RegexNode node = getNode(path);
	    while (node !=null) {
		cnt++;
		node.delAttribute_(attName);
		node = getNode(path);
	    }
	} else {
	    cnt++;
	    delAttribute_(attName);
	}
	return cnt;
    }
    private Object getAttribute_(String attName) {
	// note that the attribute may exist with object==null, or it may not exist giving object==null
	// Object ret=attributes.get(attName);
	// System.out.format("Returning attribute %s %b\n",attName,ret!=null);
	// for (Map.Entry<String, Object> entry : attributes.entrySet()) {
	//    String name   = entry.getKey();
	//    Object object = entry.getValue();
	//    if (name.equals(attName)) {
	//      System.out.format("         Found %s %b\n",name,object!=null);
	//    }
	// }
	return attributes.get(attName);
    }
    private void delAttribute_(String attName) {
	attributes.remove(attName);
    }
    /**
     * Returns attribute object.
     *
     * @param attName
     *        The name of the attribute.
     *
     * @return The attribute object.
     */
    public Object getAttribute() { // focus on next node (recursively)
	String attName=this._att;
	String[] path=this._path;
	return getAttribute(attName,path);
    }
    public Object getAttribute(String attName) { // focus on next node (recursively)
	String[] path=this._path;
	return getAttribute(attName,path);
    }
    public Object getAttribute(String attName, String[] name1, String... name2) { // focus on next node (recursively)
	return getAttribute(attName,concat(name1,name2));
    }
    public Object getAttribute(String attName, String... path) {
	Object att=null;
	if (path.length!=0) {
	    RegexNode node = getNode(path);
	    if (node !=null) {
		att=node.getAttribute_(attName);
	    }
	    getNodeReset(path);
	} else {
	    att=getAttribute_(attName);
	}
	return att;
    }
    /**
     * Returns a map over the text in the nodes and a list of the nodes with the same text.
     *
     * @param path[]
     *        Array of node names that should be matched starting with the parent 
     *        (i.e. node[0]) and ending with the node that will be processed.
     *         
     */
    public HashMap<String,ArrayList<RegexNode>> makeMap() { // focus on next node (recursively)
	String[] path=this._path;
	return makeMap((HashMap<String,ArrayList<RegexNode>> ) null,path);
    }
    public HashMap<String,ArrayList<RegexNode>> makeMap(String[] name1, String... name2) { // focus on next node (recursively)
	return makeMap((HashMap<String,ArrayList<RegexNode>> ) null,concat(name1,name2));
    }
    public HashMap<String,ArrayList<RegexNode>> makeMap(String... path) {
	return makeMap((HashMap<String,ArrayList<RegexNode>> ) null,path);
    }
    public HashMap<String,ArrayList<RegexNode>> makeMap(HashMap<String,ArrayList<RegexNode>> oldMap, String[] name1, String... name2) { // focus on next node (recursively)
	return makeMap(oldMap, concat(name1,name2));
    }
    public HashMap<String,ArrayList<RegexNode>> makeMap(HashMap<String,ArrayList<RegexNode>> oldMap, String... path) {
	HashMap<String,ArrayList<RegexNode>> ret =  oldMap;
	if (ret == null) ret=new HashMap<String,ArrayList<RegexNode>>();
	RegexNode node=getNode(path);
	while (node != null) {
	    String name=node.getText();
	    ArrayList<RegexNode> list = null;
	    list=ret.get(name);
	    if (list == null) {
		list=new ArrayList<RegexNode>();
		ret.put(name,list);
	    }
	    list.add(node);
	    node=getNode(path);
	}
	return ret;
    }

    /**
     * Returns a map over the text in the nodes and a list of the nodes with the same text.
     *
     * @param path[]
     *        Array of node names that should be matched starting with the parent 
     *        (i.e. node[0]) and ending with the node that will be processed.
     *         
     */
    public ArrayList<RegexNode> makeList() {
	String[] path=this._path;
	return makeList(path);
    }
    public ArrayList<RegexNode> makeList(String[] name1, String... name2) {
	return makeList(concat(name1,name2));
    }
    public ArrayList<RegexNode> makeList(String... path) {
	ArrayList<RegexNode> ret = new ArrayList<RegexNode>();
	RegexNode node=getNode(path);
	while (node != null) {
	    ret.add(node);
	    node=getNode(path);
	}
	return ret;
    }

    public void debugOn() {
	System.out.format("******************** D E B U G   O N ********************\n");
	debug=true;
    }
    public void debugOff() {
	debug=false;
	System.out.format("******************** D E B U G   O F F ******************\n");
    }
    //
    //
    //******************** P R I V A T E   M E T H O D S ******************
    //
    private Integer start(int num) {
	if (num==0 & nMatchGroups==-1) {
	    return 0;
	} else if (num>=0 & num <= nMatchGroups) {
	    return matchStartIndexShifted.get(num);
	} else {
	    return -1;
	}
    }
    private Integer startOriginal(int num) {
	if (num==0 & nMatchGroups==-1) {
	    return 0;
	} else if (num>=0 & num <= nMatchGroups) {
	    return matchStartIndexOriginal.get(num);
	} else {
	    return -1;
	}
    }
    private Integer end(int num) {
	if (num==0 & nMatchGroups==-1) {
	    return resultText.length();
	} else if (num>=0 & num <= nMatchGroups) {
	    return matchEndIndexShifted.get(num);
	} else {
	    return -1;
	}
    }
    private Integer endOriginal(int num) {
	if (num==0 & nMatchGroups==-1) {
	    return resultText.length();
	} else if (num>=0 & num <= nMatchGroups) {
	    return matchEndIndexOriginal.get(num);
	} else {
	    return -1;
	}
    }
    /**
     * Sets the location of all child nodes recursively.
     * The location is an array of node counts.
     * First element is the node count in the parent node.
     * Next is the parents node count in its parent node etc.
     * If only one node is present, the node count is zero.
     *
     * @param parentLocation
     *        The location of the parent.
     *
     * @param loc
     *        The node-count of this node.
     */
    private Integer setLocationAll_(ArrayList<Integer> parentLocationList, Integer loc, Integer pos) {
	ArrayList<Integer> locationList=new ArrayList<Integer>();
	locationList.clear();
	if (parentLocationList != null) locationList.addAll(parentLocationList);
	locationList.add(loc);
	loc=0;
	int level=locationList.size()-1;
	RegexNode child=firstChild.nextSibling;
	while (child != lastChild) { // last child is not a valid child
	    if (child.endFoldNode != null) { // fold starts
		pos=child.setLocationAll_(locationList,loc,pos);
		level=level+1;
		locationList.add(loc);
		loc=0;
	    } else if (child.startFoldNode != null) { // fold ends
		if (level < 0) {
		    System.out.format("invalid Fold-structure: %s\n",toString());
		    throw new IllegalStateException(String.format("Invalid Folding-state: %s.",child.getIdentification()));
		}
		loc=locationList.get(level)+1;
		locationList.remove(level);
		level=level-1;
		pos=child.setLocationAll_(locationList,loc,pos);
		loc=loc+1;
	    }else { // ordinary child
		pos=child.setLocationAll_(locationList,loc,pos);
		loc=loc+1;
	    }
	    child=child.nextSibling;
	}
	this.locationLevel=locationList.size()-1;
	this.children=loc;
	this.location=new Integer[locationList.size()];
	this.location=locationList.toArray(this.location);
	this.locationPosition=pos++;
	return pos;
    }
    public Integer getLocationLevel() {
	return locationLevel;
    }
    /**
      * Check if we have a current match available.
      *
      * @return true if we have a match, otherwise false
      */
    private boolean matchAvailable() {
	boolean available=true;
	if (available) available=matcher != null;
	if (available) available=pattern != null;
	if (available) available=oldPatternString != null;
	return available;
    }
    /**
      * Method to reset the "getNode"-search
      *
      * @param node[]
      *        Array of node names that should be matched starting with the parent 
      *        (i.e. node[0]) and ending with the node that will be returned.
      *         
      */
    private void getNodeReset_(RegexNode root, Integer sid) { // focus on next node to specified sublevel (sublevel<0: no limit; sublevel==0: no children checked)
	//System.out.format("Entering getNode %s (sublevel=%d)\n",identification,sublevel);
	RegexNode nodeChild=nodeChildMap.get(sid);  // child being currently processed in this search
	if (nodeChild != null) {
	    nodeChild.getNodeReset_(root,sid);
	}
	nodeChildMap.remove(sid);
    }
    /**
     * Method to find next node in sub-tree down to given sublevel and matching the given nodenames.
     *
     * @param sublevel
     *        number of sublevels to process
     *        Only the node itself is processed when sublevel = 0.
     *        All sublevels are processed when sublevel = -1.
     *         
     * @param path[]
     *        Array of node names that should be matched starting with the parent 
     *        (i.e. node[0]) and ending with the node that will be returned.
     *         
     */
    private RegexNode getNode_(RegexNode root,Integer sid,String[] path) { // focus on next node
	Character split = this._s;
	String nodePatternText=getPatternText(path,split);
	return getNode_(root,sid,getNewPattern(nodePatternText),split);
    }
    private RegexNode getNode_(RegexNode root, Integer sid, Pattern pattern, Character split) { // focus on next node
	if (debug) System.out.format(" getNode %s Entering with nodes: %d %s\n",getIdentification(),sid,toString());
	//System.out.format("Entering getNode %s \n",identification);
	RegexNode nodeChild=nodeChildMap.get(sid);  // child being currently processed in this search
	if (nodeChild != null) {
	    if (debug) System.out.format(" getNode %s has nodeChild defined %s\n",identification,nodeChild.identification);
	    if (nodeChild.parentNode != this) {
		System.out.format(" getNode %s Warning unexpected structure change.\n",identification);
		nodeChild=null;
		throw new IllegalStateException("Parent mismatch!");
	    }
	}
	while (true) {
	    if (nodeChild == null) {      // first node-check for this node
		nodeChild=firstChild.nextSibling;
		nodeChildMap.put(sid,nodeChild);
		if (debug) System.out.format(" getNode %s N:nodeChild defined %s\n",identification,nodeChild.identification);
	    } else if (nodeChild == lastChild) { // check this node
		nodeChild=firstChild;
		nodeChildMap.put(sid,nodeChild);
		if (nodeMatch(root,pattern,split) != null) { // finally we check this node
		    if (debug) System.out.format(" getNode %s match %s::%d.\n",identification,this.node,sid);
		    return this;
		}
		if (debug) System.out.format(" getNode %s L:nodeChild defined %s\n",identification,nodeChild.identification);
	    } else if (nodeChild == firstChild) { // we are already done here
		nodeChildMap.remove(sid);
		if (debug) System.out.format(" getNode %s No more children.\n",identification);
		return null;
	    } else if (nodeChild.identification==-99) {
		System.out.format(" getNode %s System error %s %s \n %s\n",identification,
				  nodeChild.identification,
				  nodeChild.parentNode.identification,
				  toString());
		throw new IllegalStateException("Parent mismatch!");
	    } else {
		if (debug) System.out.format(" getNode %s checking child %s \n",
					     identification,nodeChild.identification);
		RegexNode nodeSub=nodeChild.getNode_(root,sid,pattern,split);
		if (debug) System.out.format(" getNode %s checking child %s done\n",
					     identification,nodeChild.identification);
		if (nodeSub != null) {
		    if (debug) System.out.format(" getNode %s Returning with node: %s\n",getIdentification(),nodeSub.getIdentification());
		    return nodeSub;
		} else { // check next sibling
		    if (debug) System.out.format(" getNode %s Next sibling p%s c%s np%s n%s\n",
		    		      identification,nodeChild.parentNode.identification,
		    		      nodeChild.identification,nodeChild.nextSibling.parentNode.identification,
		    		      nodeChild.nextSibling.identification);
		    nodeChild=nodeChild.nextSibling;
		    nodeChildMap.put(sid,nodeChild);
		}
	    }
	}
    }
    /**
      * Checks how many levels the pattern matches this node and its parents.
      * The return value is -1 is there is no match.
      *
      * @param root
      *        Top node where the search should stop.
      *
      * @param pattern
      *        the compiled pattern that is to be matched.
      *         
      * @ split
      *        the character used to split the nodes from each other.
      */
    private Integer nodeMatch(RegexNode root, Pattern pattern, Character split) {
	if (pattern == null || split == null) { // pattern matches all way to top
	    return getNumberOfParents(root);
	}
	String parentNodeText=getParentNodeNames(root,split);
	Matcher matcher=pattern.matcher(parentNodeText);
	Integer level=0;
	if (debug) System.out.format("Matching: \"%s\" == \"%s\"",pattern,parentNodeText);
	if (matcher.find()) {
	    level = occurences(matcher.group(), split) - 2; // first level=0
	} else {
	    level=null;
	}
	if (debug) {
	    if (level != null) {
		System.out.format("   +++ LEVELS=%d\n",level);
	    } else {
		System.out.format("   ---\n");
	    }
	}
	return level;
    }
    private String getPatternText(String[] path, Character split) {
	String patternNodeText = "^"+split;
	for (int ii=path.length-1; ii >= 0;ii--) {
	    if (path[ii].equals("$")) { 
		patternNodeText=patternNodeText+"$";
	    } else if (path[ii].equals("*")) {
		patternNodeText=patternNodeText+"[a-zA-Z0-9\\.]*"+split;
	    } else if (path[ii].equals("...")) {
		patternNodeText=patternNodeText+"[a-zA-Z0-9\\."+split+"]*";
	    } else {
		patternNodeText=patternNodeText+iron(path[ii])+split;
	    }
	}
	return patternNodeText;
    }
    private Pattern getNewPattern(String nodePatternText) {
	return Pattern.compile(nodePatternText);
    }

    private Integer nodeMatchOld2(RegexNode root, String[] path) {
	Character split = this._s;
	String nodePatternText=getPatternText(path,split);
	return nodeMatch(root,getNewPattern(nodePatternText),split);
    }
    // returns the node-stack
    private String getParentNodeNames(RegexNode root,Character split) {
	String parentNodeText=split.toString();
	RegexNode current=this;
	while (current != null) {
	    parentNodeText=parentNodeText+current.getNodeName()+split;
	    if (current != root) {
		current=current.parentNode;
	    } else {
		current=null;
	    }
	}
	return parentNodeText;
    }
    // returns the node-stack
    private Integer getNumberOfParents(RegexNode root) {
	boolean foundRoot=false;
	Integer level=0;
	RegexNode current=this;
	while (current != null) { // current can never be null first time...
	    if (current == root) {
		foundRoot=true;
		current=null;
	    } else {
		current=current.parentNode;
	    }
	    level=level+1; // level above root/top parent - this is when we know we are done...
	}
	level=level-1; // reset to root/top parent
	if ( root != null & ! foundRoot ) {
	    level=null;
	}
	return level;
    }
    private boolean nodeMatchOld(RegexNode root, String[] path) {
	if(debug)System.out.format("NodeMatch Entering %s  :: %s\n",getIdentification(),getNodeName());
	if (path == null) return true;
        boolean match=true;
        RegexNode current=this;
	String s="";
        if (path != null) {
	    int ii=path.length-1;
	    if (ii >= 0) { // check if we have children while last element is "$" (lowest node in branch)
		if (path[ii].equals("$") & current.hasChildren()) { 
		    return false; // this is not the lowest node in this branch
		}
	    }
            while (current !=null & ii>=0 & match) {
		if (debug) System.out.format("   Matching: %s %s  ",current.node,path[ii],root!=null);
		if (debug & root!=null) System.out.format("   (root: %s %s)  ",root.node,root.getIdentification());
		if (path[ii].equals("...")) {
		    if (ii-1 >= 0) {
			current=nodeMatchParent(root, current,path[ii-1]);
			if (current==null) {
			    if (! path[ii-1].equals("$")) {
				match=false;
			    } else {
				ii=ii-1;
			    }
			} else {
			    ii=ii-1;
			}
		    } else { // top node-pattern was "..."
			ii=ii-1; // we are done...
		    }
		} else if (path[ii].equals("*")) {
		    ii=ii-1;
		    if (current == root) {
			current=null;
		    } else {
			current=current.parentNode;
		    };
		} else if (path[ii].equals(current.node)) {
		    ii=ii-1;
		    if (current == root) {
			current=null;
		    } else {
			current=current.parentNode;
		    };
		} else {
		    match=false;
		}
	    }
	    if (ii >= 0) {
		if (current != null) {
		    if (path[ii].equals("$")) {
			match=false;
		    }
		} else {
		    if (path[ii].equals("$")) {
			match=true;
		    } else {
			match=false;
		    }
		}
	    }
	    if (debug) {
		if (match) {
		    System.out.format("ok (%d)\n",ii);
		} else {
		    System.out.format("fail\n");
		}
	    }
	}
        return match;
    }

    private RegexNode nodeMatchParent(RegexNode root, RegexNode current, String path) {
	if (path.equals("...")) {
	    return current;
	} else if (path.equals("*")) {
	    return current;
	} else if (path.equals("$")) { // always a match
	    return null;
	}
	boolean bdone=(current==null);
	while (! bdone) {
	    if (current != null) {
		if (current.node.equals(path)) {
		    return current;
		}
	    } else if (path.equals("$")) {
		return current;
	    }
	    if (current == root) {
		current=null;
	    } else {
		current=current.parentNode;
	    };
	    bdone=(current==null);
	}
	return current;
    }

    //
    private RegexNode getParentOld_(String... path) {
	if (path != null) {
	    RegexNode current=this;
	    String s="";
	    int ii=path.length-1;
	    if (ii>0) {
		boolean match=true;
		boolean bdone=(current ==null || ii<0 || ! match);
		while (! bdone) {
		    if (path[ii].equals("...")) {
			if (ii-1 >= 0) {
			    current=nodeMatchParent(null,current,path[ii-1]);
			    if (current==null) {
				if (! path[ii-1].equals("$")) {
				    match=false;
				}
			    } else {
				ii=ii-1;
			    }
			} else {
			    bdone=true;
			}
		    } else if (path[ii].equals("*")) {
			if (ii > 0) {
			    ii=ii-1;
			    current=current.parentNode;
			    if (current==null) {
				if (! path[ii].equals("$")) {
				    match=false;
				}
			    }
			} else {
			    bdone=true;
			}
		    } else if (path[ii].equals(current.node)) {
			if (ii > 0) {
			    ii=ii-1;
			    current=current.parentNode;
			    if (current==null) {
				if (! path[ii].equals("$")) {
				    match=false;
				}
			    }
			} else {
			    bdone=true;
			}
		    } else {
			match=false;
		    }
		    if (current != null & ii >=0 ) {
			if (path[ii].equals("$")) {
			    match=false;
			}
		    }
		    bdone=(bdone || current ==null || ii<0 || ! match);
		}
		if (match) {
		    return current;
		} else {
		    return (RegexNode) null;
		}
	    } else if (ii == 0) { // one parent name specified, exact search upwards
		boolean match=false;
		boolean bdone=(current ==null || ii<0 || match);
		while (! bdone) {
		    if (path[ii].equals(current.node)) {
			match=true;
		    } else {
			ii=ii-1;
			current=current.parentNode;
		    }
		    bdone=(bdone || current ==null || ii<0 || match);
		}
		if (match) {
		    return current;
		} else {
		    return (RegexNode) null;
		}
	    } else { // no parent specified, return parent...
		return parentNode;
	    }
	} else {
	    return parentNode;
	}
    }


    //private boolean nodeMatch(String[] path) {
    //return nodeMatch(0,path);
    //}
    // private boolean nodeMatch(int subLevel,String[] path) {
    //     int ii=0;
    //     RegexNode current=this;
    //     boolean match=true;
    //     if (path != null) {
    //         while (current !=null & ii<path.length & match) {
    //             match=(path[ii].equals("*")||path[ii].equals(current.node));
    //             current=current.parentNode;
    //             ii=ii+1;
    //         }
    //         if (ii != path.length) match=false;
    //     }
    //     return match;
    // }

    /**
      * Inserts this node betweeen "first" and "last" nodes in a sibling chain, 
      * and moves any nodes between "first" and "last" to underneath this node.
      * Indexes are updated, but not the strings.
      *
      * @param first
      *        This node is inserted after "first" node.
      *         
      * @param last
      *        This node is inserted before "last" node.
      *         
      */
    private void switchWithParentChildren(RegexNode first, RegexNode last){
	RegexNode child=first.nextSibling;
	if (child!=last & first.parentNodeEndIndex>child.parentNodeStartIndex) {
	    throw new IllegalArgumentException(String.format("\n###New node:%s \n###Existing structure:%s\nAttempt by %d# to replace end of label %d#\n",
							     toString(),first.parentNode.toString(),identification,first.identification));
	}
	RegexNode target=first;
	while (child != last) { // last child is not a valid child
	    if (child.parentNodeEndIndex > parentNodeEndIndex) {
		throw new IllegalArgumentException(String.format("\n###New node:%s \n###Existing structure:%s\nAttempt by %d# to replace start of label %d#\n",
								 toString(),first.parentNode.toString(),identification, child.identification));
	    } else {
		child.parentNodeStartIndex=child.parentNodeStartIndex-parentNodeStartIndex;
		child.parentNodeEndIndex=child.parentNodeEndIndex-parentNodeStartIndex;
		RegexNode nextChild=child.nextSibling;    // point to next valid element in (parent) chain
		child.unlink();                             // detach child from original sibling chain 
		child.parentNode=this;
		lastChild.prependChain(child);
		child=nextChild;
	    }
	}
	first.appendChain(this);      // add child to this sibling chain
    }

    /**
      * Inserts children in this nodes position in sibling chain, and removes 
      * this node from the sibling chain. The next sibling is returned.
      * Indexes are updated, but not the strings.
      *
      * @return 
      *        The next sibling in the sibling chain.
      *         
      */
    private RegexNode switchWithChildren(){ // exports indexes from parents children , returns last node in chain
	RegexNode sibling=this.nextSibling;
	RegexNode child=firstChild.nextSibling;
	while (child != lastChild) { // last child is not a valid child
	    child.parentNodeStartIndex=child.parentNodeStartIndex+parentNodeStartIndex;
	    child.parentNodeEndIndex=child.parentNodeEndIndex+parentNodeStartIndex;
	    RegexNode nextChild=child.nextSibling;
	    child.unlink();
	    child.parentNode=this.parentNode;
	    sibling.prependChain(child);
	    child=nextChild;
	}
	unlink();
	return sibling;
    }

    /**
      * Private method for making a node of the specified index range.
      *
      * @param startIndex
      *        The starting index of the text that should be replaced in the resultingText.
      *
      * @param EndIndex
      *        The end index of the text that should be replaced in the resultingText.
      *
      * @param node
      *        node name given to the new node. 
      */
    private RegexNode createNode(int startIndex, int endIndex,String nodeName) {
	RegexNode child=new RegexNode(this, startIndex, endIndex);
	child.setNodeName(nodeName);
	RegexNode first=getChildBefore(startIndex);
	RegexNode last=getChildAfter(endIndex);
	child.switchWithParentChildren(first, last); // insert into sibling chain
	child.copyParentMatch(startIndex,endIndex);
	return child;
    }
    /**
     * Swap position in sibling chain
     * 
     **/
    private void changeling(RegexNode pchild, RegexNode nchild) {
	//System.out.format("Change: %d %d\n",pchild.identification,nchild.identification);
	RegexNode buff=nchild.nextSibling;
	if (pchild.nextSibling == nchild) { // neighbours
	    nchild.setNext(pchild);
	} else {
	    nchild.setNext(pchild.nextSibling);	    
	}
	pchild.setNext(buff);
	buff=pchild.prevSibling;
	if (pchild == nchild.prevSibling) { // neighbours
	    pchild.setPrev(nchild);
	} else {
	    pchild.setPrev(nchild.prevSibling);
	}
	nchild.setPrev(buff);
	nchild.nextSibling.setPrev(nchild);
	nchild.prevSibling.setNext(nchild);
	pchild.nextSibling.setPrev(pchild);
	pchild.prevSibling.setNext(pchild);
	//System.out.format("Done: %d %d\n",pchild.identification,nchild.identification);
    }
    /**
      * Private method that swaps the current match string in the resultingText and updates the match data.
      * 
      * @param resultSubstring
      *        The string that should be put into the resultingText.
      *        
      */
    private String swap_(String resultSubstring) {      // swap current match in resultText with resultSubstring
	return swap_(resultSubstring,start(0),end(0));
    }


    /**
      * Private method that swaps parts of the resultingText and updates the match data.
      * 
      * @param resultSubstring
      *        The string that should be put into the resultingText.
      *
      * @param startIndex
      *        The starting index of the text that should be replaced in the resultingText.
      *
      * @param EndIndex
      *        The end index of the text that should be replaced in the resultingText.
      *        
      */
    private String swap_(String resultSubstring,int startIndex,int endIndex) {      // swap current match in resultText with resultSubstring
	//System.out.format("Swapping:%d %d %d %s -> ",startIndex,endIndex,resultText.length(),resultText);
	String resultBuffer=resultText.substring(startIndex,endIndex);
	resultText.replace(startIndex,endIndex,resultSubstring);    // Update resultText
	return resultBuffer;
    }
    private String swap_(Plan plan) {      // swap current match in resultText with resultSubstring
	//System.out.format("Swapping:%s\n%s\n%s\n",plan.toString(),toString(),resultText);
	Shift bound=plan.getBound();
	String resultBuffer=resultText.substring(bound.getStartIndex(),bound.getEndIndex());
	resultText.replace(bound.getStartIndex(),bound.getEndIndex(),plan.getText());
	return resultBuffer;
    }
    private Plan planReplacement(String replacementText) {
	return planReplacement(replacementText, -1, null);
    }
    private Plan planReplacement(String replacementText, int group) {
	return planReplacement(replacementText, group, null);
    }
    private Plan planReplacement(String replacementText, int group, Plan plan) {
	if (plan == null) {
	    plan=new Plan();
	} else {
	    plan.reset();
	}
	if (matcher==null) {
	    //throw new IllegalStateException("No match found!");
	}
	Integer matchStart = start(group);
	Integer matchEnd   = end(group);
	Integer startShift = matchStart;
	Integer endShift   = matchEnd;
	int cnt=0;
	StringBuilder resultSubstring=new StringBuilder();
	// Process substitution string to replace group references with groups
	int cursor = 0; // position in replacementText
	while (cursor < replacementText.length()) {
	    char nextChar = replacementText.charAt(cursor);
	    if (nextChar == '\\') {
		cursor++;
		nextChar = replacementText.charAt(cursor);
		resultSubstring.append(nextChar);
		cursor++;
	    } else if (nextChar == '$') {
		// Skip past $
		cursor++;
		if (cursor >=replacementText.length()) {
		    throw new IllegalArgumentException(String.format("Illegal group reference in \"%s\"",replacementText));
		}
		// The first number is always a group
		int refNum = (int)replacementText.charAt(cursor) - '0';
		if ((refNum < 0)||(refNum > 9))
		    throw new IllegalArgumentException("Illegal group reference");
		cursor++;
		// Capture the largest legal group string
		boolean done = false;
		while (!done) {
		    if (cursor >= replacementText.length()) {
			break;
		    }
		    int nextDigit = replacementText.charAt(cursor) - '0';
		    if ((nextDigit < 0)||(nextDigit > 9)) { // not a number
			break;
		    }
		    int newRefNum = (refNum * 10) + nextDigit;
		    if (matcher.groupCount() < newRefNum) {
			done = true;
		    } else {
			refNum = newRefNum;
			cursor++;
		    }
		}
		// Append group
		Integer groupStart = start(refNum);
		Integer groupEnd   = end(refNum);
		if (groupStart != -1 && groupEnd != -1) {
		    //System.out.format("\nAppending : %d %d \"%s\" \"%s\" %d %d  %s\n",groupStart,groupEnd,resultText,
		    //		      matchGroups.get(refNum),identification,matchOffset,resultSubstring.toString());
		    resultSubstring.append(resultText, groupStart, groupEnd);
		    //System.out.format("Appended  : %d %d \"%s\" %d %s\n",groupStart,groupEnd,originalText,identification,toString());
		    if (matchStart != -1 && matchEnd != -1) {
			endShift=resultSubstring.length()-(groupEnd- matchStart);
			startShift=endShift;
			//System.out.format("Shifting %d m=(%d,%d) g=(%d,%d) s=(%d,%d)\n",
			//		  refNum,matchStart,matchEnd,groupStart,groupEnd,startShift,endShift);
			//if (matchStart==groupStart) { startShift=0;}; // change is within index
			// shift any child Indexes...
			// System.out.format("#####Index search: %d %d %d \"%s\"\n",matchStart,matchEnd,endShift,resultSubstring);
			cnt=cnt+1;
			plan.addShift(new Shift(refNum,groupStart,groupEnd,startOriginal(refNum),endOriginal(refNum),startShift,endShift)); // type=cut-and-paste
		    }
		} else  {
		    System.out.format("No match/group available (%d), Only $0 allowed in replacementText \"%s\"\n\"%s\"\n",group,replacementText,toString());
		    throw new IndexOutOfBoundsException("No group " + refNum);
		}
		//System.out.format("Found group: m(%d %d) %d %d\n",matchStart,matchEnd,groupStart,groupEnd);
	    } else {
		resultSubstring.append(nextChar);
		cursor++;
	    }
	    
	}
	startShift=0;
	endShift=resultSubstring.length()-(matchEnd-matchStart);
	if (matchStart != -1 && matchEnd != -1) {
	    plan.setBound(new Shift(group,matchStart,matchEnd,startOriginal(group),endOriginal(group),startShift,endShift)); // type=replacement
	}
	plan.setText(resultSubstring.toString());
	return plan;
    }
    /**
      * Private method that makes replacement string and updates the indexes of active children.
      * This method is similar to "appendReplacement" in the "Matcher" class.
      * 
      * @param replacementText
      *        The replacement rule following standard regex syntax ("$1" is group 1 etc.)
      */
    private Plan planReplacement(String replacementText, int startIndex, int endIndex) { // get processed replacement and update active child indexes within match before making them inactive
	return planReplacement(replacementText, startIndex, endIndex,-1,-1,null);
    }
    private Plan planReplacement(String replacementText, int startIndex, int endIndex, int startIndexOriginal, int endIndexOriginal, Plan plan) { // get processed replacement and update active child indexes within match before making them inactive
	if (replacementText == null) replacementText="";
	if (plan == null) {
	    plan=new Plan();
	} else {
	    plan.reset();
	}
	int startShift=0;
	int endShift=replacementText.length()-(endIndex-startIndex);
	int cnt=0;
	int group=0;
	plan.setBound(new Shift(group,startIndex,endIndex,startIndexOriginal,endIndexOriginal,startShift,endShift));
	plan.setText(replacementText);
	return plan;
    }
    private Plan planReplacement(int startIndex, int endIndex, int startIndexOriginal, int endIndexOriginal, Plan plan) { // get processed replacement and update active child indexes within match before making them inactive
	if (plan == null) {
	    plan=new Plan();
	} else {
	    plan.reset();
	}
	int startShift=0;
	int endShift=0;
	int cnt=0;
	int group=0;
	plan.setBound(new Shift(group,startIndex,endIndex,startIndexOriginal,endIndexOriginal,startShift,endShift));
	plan.setText(resultText.substring(startIndex,endIndex));
	return plan;
    }
    private class Plan {
	String resultText="";
	List<Shift> shifts=new ArrayList<Shift>();
	//HashMap<Integer,Integer> groupCount=new HashMap<Integer,Integer>();
	Shift bound;
	Plan() {
	}
	void reset() {// empty stack, reset string
	    resultText="";
	    shifts.clear();
	}
	void setText(String resultText) {
	    this.resultText=resultText;
	}
	String getText() {
	    return this.resultText;
	}
	void addShift(Shift shift) {
	    shifts.add(shift);
	    //incrementCount(shift);
	}
	void addShift() {
	    shifts.add(bound);
	}
	// void incrementCount(Shift shift) {
	//     incrementCount(shift.group);
	// }
	// void incrementCount(Integer group) {
	//     Integer cnt=groupCount.get(group);
	//     if (cnt == null) cnt=0;
	//     groupCount.put(group,cnt+1);
	// }
	// void decrementCount(Shift shift) {
	//     decrementCount(shift.group);
	// }
	// void decrementCount(Integer group) {
	//     Integer cnt=groupCount.get(group);
	//     if (cnt == null) cnt=0;
	//     groupCount.put(group,cnt-1);
	// }
	// Integer getCount(Shift shift) {
	//     return groupCount.get(shift.group);
	// }
	// Integer getCount(Integer group) {
	//     return groupCount.get(group);
	// }
	Shift[] getShift() {
	    return shifts.toArray(new Shift[shifts.size()]);
	}
	void setBound(Shift shift) {
	    bound=shift;
	}
	Shift  getBound() {
	    return bound;
	}
	int getStartIndex(Plan plan) { // 
	    int startMatch=bound.getStartIndex();
	    int endMatch=bound.getEndIndex();
	    int startMatchShift=bound.getStartShift();
	    int endMatchShift=bound.getEndShift();
	    return startMatch+startMatchShift;
	    
	}
	int getEndIndex(Plan plan) { // 
	    int startMatch=bound.getStartIndex();
	    int endMatch=bound.getEndIndex();
	    int startMatchShift=bound.getStartShift();
	    int endMatchShift=bound.getEndShift();
	    return endMatch+endMatchShift;
	}
	public String toString() {
	    String s="Text:\""+resultText+"\" ";
	    if (bound != null) {
		s=s+"BBox:"+bound.toString();
	    }
	    for (Shift shift : shifts) {
		s=s+" GBox:"+shift.toString();
	    }
	    return s.replaceAll("[^\\x00-\\x7F\\xA4]", "§");
	}
    }

    private class Shift {
	int group;
	int startIndex;
	int endIndex;
	int startIndexOriginal;
	int endIndexOriginal;
	int startShift;
	int endShift;
	Shift(int group, int startIndex, int endIndex, int startIndexOriginal, int endIndexOriginal, int startShift, int endShift) {
	    this.group=group;
	    this.startIndex=startIndex;
	    this.endIndex=endIndex;
	    this.startIndexOriginal=startIndexOriginal;
	    this.endIndexOriginal=endIndexOriginal;
	    this.startShift=startShift;
	    this.endShift=endShift;
	    if (startIndex < 0 || 
		startIndex + startShift < 0 || 
		endIndex < 0 || 
		endIndex + endShift < 0) {
		throw new IllegalStateException(String.format(
                     "Invalid shift limits: group=%d(%d+%d,%d+%d)",
		     group,startIndex,startShift,endIndex,endShift));
	    }
	}
	int getGroup() {
	    return group;
	}
	int getStartIndex() {
	    return startIndex;
	}
	int getEndIndex() {
	    return endIndex;
	}
	int getStartIndexOriginal() {
	    return startIndexOriginal;
	}
	int getEndIndexOriginal() {
	    return endIndexOriginal;
	}
	int getStartShift() {
	    return startShift;
	}
	int getEndShift() {
	    return endShift;
	}
	public String toString() {
	    String s=String.format(" group=%d(%d+%d,%d+%d)",group,startIndex,startShift,endIndex,endShift);
	    return s;
	}
    }

    /**
      * method to shift this node so it matches the given plan bounds.
      * The method assumes no offset!
      *
      * @param plan
      *        plan that should be matched.
      */
    private void shiftIndexesSoTheyMatch(Plan plan) { // 
	Shift bound=plan.getBound();
	int startMatch=bound.getStartIndex();
	int endMatch=bound.getEndIndex();
	int startMatchShift=bound.getStartShift();
	int endMatchShift=bound.getEndShift();
	parentNodeStartIndex=startMatch+startMatchShift;
	parentNodeEndIndex=endMatch+endMatchShift;
	//System.out.format("ShiftSTM: (%d %d) (%d+%d,%d+%d)\n", 
	//		  parentNodeStartIndex,parentNodeEndIndex,
	//		  startMatch,startMatchShift,endMatch,endMatchShift);
    }
    private void shiftSiblingChainIndexes(Plan plan) { // shift children according to plan
	Shift[] shifts=plan.getShift();
	Shift bound=plan.getBound();
	int startMatch=bound.getStartIndex();
	int endMatch=bound.getEndIndex();
	int startMatchShift=bound.getStartShift();
	int endMatchShift=bound.getEndShift();
	RegexNode child=nextSibling;
	if (child == null) {
	    System.out.format("Orphan child %s\n",toString());
	    throw new IllegalStateException(String.format("Orphan child %s\n",toString()));
	} else {
	    while(child != parentNode.lastChild) {
	    RegexNode nextChild=child.nextSibling;    // point to next valid element in chain
	    int startType=getType(child.parentNodeStartIndex,startMatch,endMatch);
	    int endType=getType(child.parentNodeEndIndex,startMatch,endMatch);
	    //System.out.format("shiftSiblingChainIndexes Shifting %d-siblings, Id=%d  S=%s  E=%s %s\n",
	    //			   this.identification,child.identification,
	    //			   getTypeString(startType),getTypeString(endType),
	    //			   child.toString());
	    // Do not shift indexes that are outside the group or that 
	    // are empty and point to the start of the group.
	    //
	    // check if both startType and endType are within match
	    if (startType!=before & startType!=after & 
		endType  !=before & endType  !=after &  
		startType != atBoth &  endType  != atBoth) { 
		// loop over group regions
		Integer count=0;
		HashMap<Integer,Integer> startShift =new HashMap<Integer,Integer>();
		HashMap<Integer,Integer> endShift =new HashMap<Integer,Integer>();
		for (Shift shift : shifts) {
		    int startGroup =shift.getStartIndex();
		    int endGroup   =shift.getEndIndex();
		    int startGroupShift =shift.getStartShift();
		    int endGroupShift   =shift.getEndShift();
		    // check if child is within the data region
		    int startGroupType=getType(child.parentNodeStartIndex,startGroup,endGroup);
		    int endGroupType=getType(child.parentNodeEndIndex,startGroup,endGroup);
		    if (startGroupType!=before & startGroupType!=after & 
			endGroupType!=before & endGroupType!=after ) {
			//System.out.format("shiftSiblingChainIndexes Shift:  count=%d  pos=(%d,%d)  shift=(%d,%d)\n",
			//count,
			//		  child.parentNodeStartIndex,child.parentNodeEndIndex,
			//		  startGroupShift,endGroupShift);
			startShift.put(count,child.parentNodeStartIndex + startGroupShift);
			endShift.put(count,child.parentNodeEndIndex + endGroupShift);
			count=count+1;
		    }
		}
		if (count > 0) {
		    if (child.parentNodeStartIndex==child.parentNodeEndIndex) count=1;
		    for (Integer ii=1; ii<count; ii++) { // make duplicates
			//System.out.format("Shift duplicating: %s\n",child.toString());
			RegexNode twin=child.duplicate();
			twin.parentNode=parentNode;
			twin.parentNodeStartIndex=startShift.get(ii);
			twin.parentNodeEndIndex=endShift.get(ii);
			child.prependChain(twin);
			twin.positionAfter(this);
                        // TWIN PARENTNODESTARTINDEX IS ALL WRONG (DOES NOT CHANGE!)
			//System.out.format("Duplicate: %s\n%s\n",twin.toString(),child.toString());

		    }
		    child.parentNodeStartIndex=startShift.get(0);
		    child.parentNodeEndIndex=endShift.get(0);
		    child.positionAfter(this);
		} else { // child within match, but not shifted...!
		    throw new IllegalStateException(String.format("%s\nPlan=%s\n*** No plan to shift node %d ***\n",
								  parentNode.toString(),plan.toString(),child.identification));
		}
	    } else {
		if (startType==before & (endType==between || endType==atEnd) ) {
		    if (endMatch != startMatch) {
			throw new IllegalStateException(String.format("\nAttempt to shift end of node #%d only (%d %d).\n%s",
								      child.identification,startMatch,endMatch,parentNode.toString()));
		    }
		} else if ((startType==atStart || startType==between) & endType==after ) {
		    if (endMatch != startMatch) {
			throw new IllegalStateException(String.format("\nAttempt to shift start of node #%d only (%d %d).\n%s\n%s\n",
								      child.identification,startMatch,endMatch,parentNode.toString(),plan.toString()));
		    }
		} else if (startType == atBoth ||
			   startType == atEnd || 
			   startType == after) {
		    // after match, shift start/end-indexes by endMatchShift...
		    //System.out.format("shiftSiblingChainIndexes After-shift:pos=(%d,%d)  shift=%d",
		    //		      child.parentNodeStartIndex,child.parentNodeEndIndex,
		    //		      endMatchShift);
		    child.parentNodeStartIndex=child.parentNodeStartIndex + endMatchShift;
		    child.parentNodeEndIndex=child.parentNodeEndIndex + endMatchShift;
		    //System.out.format("   newpos=(%d,%d)\n",
		    //		      child.parentNodeStartIndex,child.parentNodeEndIndex);
		}
	    }
	    child=nextChild;
	}
	}
    }

    private void shiftMatchIndexes (Plan plan) {
	// index shifted according to first occurence in replacementString
	boolean[] found=new boolean[Math.max(1,nMatchGroups+1)];
	for (int ii=0;ii<=nMatchGroups;ii++) {
	    found[ii]=false;
	}
	// get the group that was replaced...
	Shift bound=plan.getBound();
	int startMatch=bound.getStartIndex();
	int endMatch=bound.getEndIndex();
	int startMatchShift=bound.getStartShift();
	int endMatchShift=bound.getEndShift();
	int groupMatch=bound.getGroup();
	if (startMatch ==-1 & endMatch==-1) {
	    matchStartIndexShifted.put(groupMatch,startMatch);
	    matchEndIndexShifted.put(groupMatch,endMatch);
	} else {
	    matchStartIndexShifted.put(groupMatch,startMatch+startMatchShift);
	    matchEndIndexShifted.put(groupMatch,endMatch+endMatchShift);
	}
	//System.out.format("Bound:%s %d\n",bound.toString(),nMatchGroups);
	if (nMatchGroups > 0) {
	    found[groupMatch]=true;
	}
	//if (groupMatch == 0) {
	matchOffset=matchOffset+endMatchShift;
	//}
	// check if group is used in replacementString
	Shift[] shifts=plan.getShift();
	int len=shifts.length;
	for (Shift shift : shifts) {
	    int startGroup =shift.getStartIndex();
	    int endGroup   =shift.getEndIndex();
	    int startShift =shift.getStartShift();
	    int endShift   =shift.getEndShift();
	    int group      =shift.getGroup();
	    // find out if each group is shifted more than once...
	    if (! found[group]) {
		if (startGroup==-1 & endGroup==-1) {
		    matchStartIndexShifted.put(group,startGroup);
		    matchEndIndexShifted.put(group,endGroup);
		    matchStartIndexOriginal.put(group,startGroup);
		    matchEndIndexOriginal.put(group,endGroup);
		} else {
		    matchStartIndexShifted.put(group,startGroup+startShift);
		    matchEndIndexShifted.put(group,endGroup+endShift);
		    matchStartIndexOriginal.put(group,startGroup);
		    matchEndIndexOriginal.put(group,endGroup);
		}
		found[group]=true;
	    }
	}
	// process groups outside replacement region
	for (Integer ii=0;ii<=nMatchGroups;ii++) {
	    if (!found[ii]) {
		if (start(ii)==startMatch & end(ii)==endMatch) {
		    matchEndIndexShifted.put(ii,end(ii)+endMatchShift);
		    //System.out.format("=========== All \n%s\n",toString());
		} else {
		    if (start(ii) >= endMatch & end(ii) >= endMatch) {
			matchStartIndexShifted.put(ii,start(ii)+endMatchShift);
			matchEndIndexShifted.put(ii,end(ii)+endMatchShift);
		    } else if (start(ii) > startMatch & end(ii) < endMatch) {
			nMatchGroups=-1;
		    } 
		}
	    }
	}
    }
    /**
      * Create match containing the whole node.
      *
      * @param startIndex
      *        start index in parent.
      *
      * @param endIndex
      *        end index in parent.
      */
    private void copyParentMatch(int startIndex, int endIndex) {
	nMatchGroups=0;
	if (startIndex==-1 & endIndex==-1) {
	    matchStartIndexShifted.put(0,startIndex);
	    matchEndIndexShifted.put(0,endIndex);
	    matchStartIndexOriginal.put(0,startIndex);
	    matchEndIndexOriginal.put(0,endIndex);
	    matchGroups.put(0,"");
	} else {
	    matchStartIndexShifted.put(0,0);
	    matchEndIndexShifted.put(0,endIndex-startIndex);
	    matchStartIndexOriginal.put(0,0);
	    matchEndIndexOriginal.put(0,endIndex-startIndex);
	    matchGroups.put(0,getText());
	}
    }
    /**
      * Copies match data from parent.
      */
    private void copyParentMatch() {
	copyParentMatch(0);
    }
    /**
      * Copies match data from parent.
      *
      * @param group
      *        only copy specified group data (0=all).
      */
    private void deleteMatchRange(int group) {
	int startIndex=matchStartIndexOriginal.get(group);
	int endIndex=matchEndIndexOriginal.get(group);
	deleteMatchRange(startIndex,endIndex);
    };
    private void deleteMatchRange(int startIndex, int endIndex) {
	//if (debug) 
	System.out.format("Deleting range: %s %d->%d\n",getIdentification(),startIndex,endIndex);
	for (Integer ii=0;ii<=nMatchGroups;ii++) {
	    if (startIndex <= matchStartIndexShifted.get(ii) &&
		endIndex >= matchEndIndexShifted.get(ii)) {
		deleteMatch(ii);
	    };
	};
    };
    private void deleteMatch(int group) {
	matchStartIndexShifted.put(group,null);
	matchEndIndexShifted.put(group,null);
    };
    private void copyParentMatch(int group) {
	copyParentMatch(group, parentNode.start(group), parentNode.end(group));
    }
    private void copyParentMatch(int group, int startIndex, int endIndex) {
	if (! parentNode.matchResultChanged) {
	    if (group==0) {
		nMatchGroups=parentNode.nMatchGroups;
		for (Integer ii=0;ii<=nMatchGroups;ii++) {
		    int s=parentNode.start(ii);
		    int e=parentNode.end(ii);
		    if (s==-1 & e==-1) {
			matchStartIndexShifted.put(ii,parentNode.start(ii));
			matchEndIndexShifted.put(ii,parentNode.end(ii));
			matchStartIndexOriginal.put(ii,parentNode.start(ii));
			matchEndIndexOriginal.put(ii,parentNode.end(ii));
			matchGroups.put(ii,"");
		    } else {
			matchStartIndexShifted.put(ii,parentNode.start(ii)-startIndex);
			matchEndIndexShifted.put(ii,parentNode.end(ii)-startIndex);
			matchStartIndexOriginal.put(ii,parentNode.start(ii)-startIndex);
			matchEndIndexOriginal.put(ii,parentNode.end(ii)-startIndex);
			matchGroups.put(ii,parentNode.matchGroups.get(ii));
		    }
		}
	    } else {
		nMatchGroups=0;
		int s=parentNode.start(group);
		int e=parentNode.end(group);
		if (s==-1 & e==-1) {
		    matchStartIndexShifted.put(0,parentNode.start(group));
		    matchEndIndexShifted.put(0,parentNode.end(group));
		    matchStartIndexOriginal.put(0,parentNode.start(group));
		    matchEndIndexOriginal.put(0,parentNode.end(group));
		    matchGroups.put(0,"");
		} else {
		    matchStartIndexShifted.put(0,parentNode.start(group)-startIndex);
		    matchEndIndexShifted.put(0,parentNode.end(group)-startIndex);
		    matchStartIndexOriginal.put(0,parentNode.start(group)-startIndex);
		    matchEndIndexOriginal.put(0,parentNode.end(group)-startIndex);
		    matchGroups.put(0,parentNode.matchGroups.get(group));
		}
	    }
	}
    }

    /**
      * Retrieves ignore-flag for specified node.
      * Throws error if nodes with same name has different ignore status.
      * 
      * @param node
      *        node name of the nodes for which the ignore-flag should be retrieved.
      */
    private Boolean getIgnore(String node, Boolean ignore) { // ignored nodes are not "un-hidden" by "unhide"
	if (node.equals(this.node)) {
	    if (ignore == null) {
		ignore=ignored;
	    } else {
		if ((ignore & ! ignored) || (!ignore & ignored)) {
		    throw new IllegalStateException(String.format("\nNode %s is not uniformly ignored.\n%s",node,toString()));
		}
	    };
	}
	RegexNode child=firstChild.nextSibling;
	while(child != lastChild) {
	    ignore=child.getIgnore(node,ignore);
	    child=child.nextSibling;    // point to next valid element in chain
	}
	return ignore;
    }
    
    private RegexNode ignoreAll_(RegexNode root, Pattern pattern,Character split) { // ignored nodes are not "un-hidden" by "unhide"
	RegexNode child=firstChild.nextSibling;
	while(child != lastChild) {
	    child.ignoreAll_(root,pattern,split);
	    child=child.nextSibling;    // point to next valid element in chain
	}
	if (parentNode != null & nodeMatch(root,pattern,split) != null) {
	    ignore();
	}
	return this;
    }
	
    private RegexNode unignoreAll_(RegexNode root, Pattern pattern, Character split) {
	RegexNode child=firstChild.nextSibling;
	while(child != lastChild) {
	    child.unignoreAll_(root,pattern,split);
	    child=child.nextSibling;    // point to next valid element in chain
	}
	if (nodeMatch(root,pattern,split) != null)  {
	    unignore();
	}
	return this;
    }

    /**
      * Check where a cursor position is with respect to lower and upper limits.
      *
      * @param pos
      *        position in string, which is never negative.
      *
      * @param s
      *        Lower limit.
      *
      * @param e
      *        Upper limit.  Can be "nolimit" if there is no upper limit.
      *
      * @return 
      *        returns an integer which can be any of the variables:
      *        "before", "atStart", "between", "atEnd" or "after"
      */
    private int getType(int pos,int s,int e) {
	int type=0;
	if (pos < s) {
	    type=before;
	} else if (pos == s) {
	    if (pos == e) {
		type=atBoth;
	    } else {
		type=atStart;
	    }
	} else if (e==nolimit || (s < pos  &  pos < e)) {
	    type=between;
	} else if (pos == e) {
	    type=atEnd;
	} else if (e < pos) {
	    type=after;
	};
	return type;
    }

private String getTypeString(int type) {
	if (type==nolimit) {
	return "NoLimit";
	} else if (type==before) {
	    return "Before";
	} else if (type==atBoth) {
	    return "AtBoth";
	} else if (type==atStart) {
	    return "AtStart";
	} else if (type==between) {
	    return "Between";
	} else if (type==atEnd) {
	    return "AtEnd";
	} else if (type==after) {
	    return "After";
	} else  {
	    return "Undefined";
	}
    }
    /**
     * Private method to initiate sibling chain and the originalText
      * @param  OriginalText
      *         The text that we want to search and process.
      */
    private void init(String originalText) {
	initSiblingChain();
	useOriginalString(originalText);
    }

    /**
     * Private method to initiate sibling chain.
      */
    private void initSiblingChain() {
	// set identification
	identification=++maxidentification;
	// set sibling chain
	this.firstChild=new RegexNode(this,-1,-1);
	this.lastChild=new RegexNode(this,-1,-1);
	this.firstChild.setNext(this.lastChild);
	this.lastChild.setPrev(this.firstChild);
    }

    /**
      * Public method to change the search text
      * @param  OriginalText
      *         The text that we want to search and process.
      */
    public void useOriginalString(String originalText) {
	if (originalText==null) {
	    System.out.format("Attempt to use NULL as original string.\n");
	    originalText="";
	}
	String old=null;
	if (this.originalText != null) {
	    old=this.originalText.toString();
	}
	//removeChildren();
	if (! originalText.equals(old)) {
	    //System.out.format(":::::::::::::::::::Resetting original text\n");
	    this.originalText=new StringBuffer(originalText);
	    this.resultText=new StringBuilder(originalText);
	    matchStartIndexShifted.put(0,0);
	    matchEndIndexShifted.put(0,originalText.length());
	    matchStartIndexOriginal.put(0,0);
	    matchEndIndexOriginal.put(0,originalText.length());
	    matchGroups.put(0,originalText);
	    nMatchGroups=-1;
	}
	matchOffset=0;
	matchPos=0;
    }

    /**
      * Public method to remove all children from the sibling chain.
      */
    public void removeChildren(){  // remove children that point to this node
	RegexNode child=firstChild.nextSibling;
	while (child != lastChild) {    // last child is not a valid child
	    child.removeChildren();     // remove child dependencies
	    child=child.nextSibling;    // point to next valid element in chain
	    child.prevSibling.unlink(); // remove previous child from sibling chain
	}	    
    }
    /**
      * Private method to add node to the sibling chain of this node
      * @param child
      *        child is inserted after this node
      */
    private void appendChain(RegexNode child) { // add child to sibling chain after this node
	if (child.parentNode != parentNode) {
	    System.out.format("Parent mismatch: %d %d\n%s\n",child.parentNode.identification,
			      parentNode.identification, parentNode.toString());
	    throw new IllegalStateException("Parent mismatch in chain!");
	}
	if (nextSibling != null) {
	    nextSibling.setPrev(child);
	};
	if (child != null) {
	    child.setPrev(this);
	    child.setNext(nextSibling);
	};
	nextSibling=child;
    }
    /**
      * Private method to add node to sibling chain of this node
      * @param child
      *        child is inserted before this node
      */
    private void prependChain(RegexNode child) { // add child to sibling chain before this node
	if (child.parentNode != parentNode) {
	    System.out.format("Parent mismatch: %d %d\n%s\n",child.parentNode.identification,
			      parentNode.identification, parentNode.toString());
	    throw new IllegalStateException("Parent mismatch in chain!");
	}
	if (prevSibling != null) {
	    prevSibling.setNext(child);
	};

	if (child != null) {
	    //System.out.format("Prepending chain: %s\n",child.toString());
	    child.setPrev(prevSibling);
	    child.setNext(this);
	};
	prevSibling=child;
    }

    /**
      * Private method to bubble-sort node down in sibling chain.
      *
      * @param child
      *        first child that node should NOT be sorted below.
      */
    private void positionAfter(RegexNode child) { // add child to sibling chain before this node
	RegexNode first=parentNode.firstChild;
	boolean bdone=(prevSibling == first || prevSibling == child ||
		       parentNodeStartIndex >= prevSibling.parentNodeStartIndex);
	while (! bdone) {
	    RegexNode buffChild=prevSibling;
	    buffChild.unlink();           // remove from sibling chain
	    appendChain(buffChild); // add to chain after child
	    bdone=(prevSibling == first || prevSibling == child ||
		   parentNodeStartIndex >= prevSibling.parentNodeStartIndex);
	}
    }
    /**
      * Private method to make sibling chain link
      * @param next
      *        next sibling is set to next
      */
    private void setNext(RegexNode next) {
	if (next.parentNode != parentNode) {
	    System.out.format("Parent mismatch: %d %d\n%s\n",next.parentNode.identification,
			      parentNode.identification, parentNode.toString());
	    throw new IllegalStateException("Parent mismatch in chain!");
	}
	this.nextSibling=next;
    }

    /**
      * Private method to make sibling chain link
      * @param prev
      *        previous sibling is set to prev
      */
    private void setPrev(RegexNode prev) {
	if (prev.parentNode != parentNode) {
	    System.out.format("Parent mismatch: %d %d\n%s\n",prev.parentNode.identification,
			      parentNode.identification, parentNode.toString());
	    throw new IllegalStateException("Parent mismatch in chain!");
	}
	this.prevSibling=prev;
    }

    /**
      * An anchor is a non-standard character that can be used to create unambiguous labels.
      *        
      */
    private static String anchor_() { // 
	char c=(char) 128;
	return String.valueOf(c);
    }
    /**
      * unlink this node from its sibling chain.
      */
    private void unlink() { // unlink this node from sibling chain
	if (prevSibling != null) {
	    prevSibling.setNext(nextSibling);
	};
	if (nextSibling != null) {
	    nextSibling.setPrev(prevSibling);
	};
	prevSibling=null;
	nextSibling=null;
    }
    /**
      * Hides the current match giving it a node. 
      * A replacement rule is applied to the hidden text based on the current match.
      * The hidden text is replaced by a label in the search text.
      *        
      * @param node
      *        The name of the node given to the hidden text.
      *
      * @param label
      *        label to replace the hidden text in the search text.
      *
      * @param group
      *        The group that should be hidden (0 = entire match).
      */
    private RegexNode hide_(String nodeName, String label, int group) { // hide current match 
	int s=start(Math.abs(group));
	int e=end(Math.abs(group));
	if (s==-1 &e==-1) {
	    throw new IllegalStateException(String.format("%s\nAttempt to replace non-existent group %d in node %s.\n",toString(),group,identification));
	};
	if (group < 0 && s==e) return null; // empty groups are omitted if group-id is negative...
	//System.out.format("\n\n%s\nHiding: %d group %d (%d %d)\n",toString(),identification,group,start(Math.abs(group)),end(Math.abs(group)));
	RegexNode child=new RegexNode(this, s, e);
	child.setNodeName(nodeName);
	RegexNode first=getChildBefore(s);
	RegexNode last=getChildAfter(e);
	child.switchWithParentChildren(first, last); // copy match groups to new node
	child.copyParentMatch(Math.abs(group));  // copy match groups to new node
	child.setLabel_(label);         // change label in parent result string
	return child;
    }
    /**
      * Hides everything between "first" child node and "last" child node
      * The hidden text is replaced by a label in the search text.
      *        
      * @param node
      *        The name of the node given to the hidden text.
      *
      * @param label
      *        label to replace the hidden text in the search text.
      *
      * @param first
      *        The first child node that should be hidden.
      *
      * @param last
      *        The last child node that should be hidden.
      */
    private RegexNode hide_(String nodeName, String label, RegexNode first, RegexNode last) { // hide from
	int s=first.parentNodeEndIndex;
	int e=last.parentNodeStartIndex;
	if (s==-1) s=0; // if firstChild is used, hide from beginning
	if (e==-1) e=resultText.length(); // if lastChild is used, hide until the end
	if (first.nextSibling==last && s>=e) return null; // nothing to hide...
	RegexNode child=new RegexNode(this, s, e);
	child.setNodeName(nodeName);
	child.switchWithParentChildren(first, last); // copy match groups to new node
	child.setLabel_(label);         // change label in parent result string
	return child;
    }
    /**
      * Hides everything between startIndex and endIndex in this node.
      * The hidden text is replaced by a label.
      *        
      * @param nodeName
      *        The name of the node given to the hidden text.
      *
      * @param label
      *        label to replace the hidden text in the search text.
      *
      * @param first
      *        The first child node that should be hidden.
      *
      * @param last
      *        The last child node that should be hidden.
      */
    private RegexNode hide_(String nodeName, String label, int startIndex, int endIndex, RegexNode child) { // hide current match 
	if (endIndex==-1) endIndex=resultText.length(); // if lastChild is used, hide until the end
	if (startIndex==endIndex) return null; // nothing to hide...
	if (child==null) {
	    child=new RegexNode(this, startIndex, endIndex);
	} else {
	    child.unlink();
	    child.parentNode=this;
	    child.parentNodeStartIndex=startIndex;
	    child.parentNodeEndIndex=endIndex;
	    child.useOriginalString(resultText.substring(startIndex,endIndex));
	}
	child.setNodeName(nodeName);
	RegexNode first=getChildBefore(startIndex);
	RegexNode last=getChildAfter(endIndex);
	child.switchWithParentChildren(first, last); // copy match groups to new node
	//child.copyParentMatch(0);  // copy match groups to new node
	child.setLabel_(label);
	//Plan plan=replace(label,startIndex,endIndex);
	return child;
    }


    /**
      * Hides text that has not been nodeged so far, and replaces it with a label.
      *
      * @param node
      *        Name of node name given to the hidden text.
      *         
      * @param label
      *        Label put in place of the hidden text.
      *         
      */
    private boolean hideTheRestAny_(RegexNode root, String newnode, String label, Pattern pattern, Character split) {
	boolean hit=false;
	if (! ignored & nodeMatch(root,pattern,split) != null) {
	    hideTheRest(newnode,label);
	    hit=true;
	} else if (! ignored) {
	    RegexNode child=firstChild.nextSibling;
	    while (child != lastChild) { // last child is not a valid child
		child.hideTheRestAny_(root,newnode,label,pattern, split); // child always has a parent, so the return value can not be null
		hit=true;
		child=child.nextSibling;
	    };
	}
	return hit;
    }
    private boolean setNodeNameAll_(RegexNode root, String newnode, Pattern pattern, Character split) {
	boolean ret=false;
	boolean active = (! ignored);
	boolean match=(active & pattern !=null & nodeMatch(root,pattern,split) != null);
	if (active) {
	    RegexNode child=firstChild.nextSibling;
	    while (child != lastChild) { // last child is not a valid child
		ret=(child.setNodeNameAll_(root,newnode,pattern,split) || ret);
		child=child.nextSibling;
	    };
	}
	if (match) {
	    ret=(setNodeName(newnode) || ret);
	}
	return ret;
    }
    
    private RegexNode getChildBefore(int startIndex) {
	RegexNode child=firstChild.nextSibling;
	boolean found=false;
	while (!found & child != lastChild) { // last child is not a valid child
	    if (child.parentNodeStartIndex >= startIndex) {
		found=true;
	    } else {
		child=child.nextSibling;
	    }
	}
	return child.prevSibling;
    }

    private RegexNode getChildAfter(int endIndex) {
	RegexNode child=firstChild.nextSibling;
	boolean found=false;
	while (!found & child != lastChild) { // last child is not a valid child
	    if (child.parentNodeStartIndex >= endIndex) {
		found=true;
	    } else {
		child=child.nextSibling;
	    }
	}
	return child;
    }
    /**
      * Hides all occurences of the specified pattern, giving them the same node name.
      * Only the calling node is searched.
      *
      * @param patternText
      *        The regular expression that it is searched for.
      *        
      * @param node
      *        The name of the node given to the hidden text.
      *
      * @param label
      *        label to replace the hidden text in the search text.
      *
      * @param targetlevel
      *        number of targetlevels to process
      *        Only the node itself is processed when targetlevel = 0.
      *        All targetlevels are processed when targetlevel = -1.
      *
      * @param subNodes
      *        number of targetlevels to process below the target-nodes.
      *        Only the target-nodes themselves are processed when subNodes = 0.
      *        All targetlevels below target-nodes are processed when subNodes = -1.
      *
      * @param node...
      *        node name stack for the node that should be processed 
      *        starting with the parent node in the node tree, 
      *        and ending with the name of the node that will be 
      *        processed ("..." is one or more nodes, "*" is any node, 
      *        "$" indicates top of the node tree).
      */
    private boolean hideAny_(RegexNode root, int targetlevel, String node, String patternText, 
			     String label,Pattern pattern, Character split) { // hide current match 
	boolean hit=false;
	RegexNode child=firstChild.nextSibling;
	this.marked=false;
	while (child!= lastChild & !ignored) { // last child is not a valid child
	    if (! child.ignored) {
		if (child.hideAny_(root, targetlevel, node, patternText, label, pattern,split)) {
		    hit=true;
		};
	    };
	    child=child.nextSibling;
	}
	boolean match = (! ignored);
	Integer imatch=nodeMatch(root,pattern,split);
	if (match & pattern !=null) match= imatch != null;
	if (match) {
	    // mark parent
	    RegexNode childNode=this;
	    int level =0;
	    Integer tlevel = targetlevel;
	    if (targetlevel < 0) {
		tlevel = imatch + 1 + targetlevel; // count from top, -1 being top level in node match
	    }
	    while (level < tlevel & childNode != null & childNode != root){
		level=level+1;
		childNode=childNode.parentNode;
	    }
	    if (level == targetlevel & childNode != null) {
		childNode.marked=true;
	    }
	}
	if (marked) {  // delayed processing...
	    //System.out.format("Hiding %s (%s)\n",getIdentification(),patternText);
	    try {
		while (seek_(patternText)) {
		    if (!this.node.equals(node)) { // do not hide if this node has same node name as the proposed child node
			hide_(node,label,0);
			hit=true;
		    }
		}
	    } catch (NullPointerException e) {
		System.out.format("Error hiding %s\n%s\n",identification, toString());
		throw e;
	    }
	}
	return hit;
    }
    /**
      * Unhides occurences of the specified node name.
      *
      * @param node
      *        The name of the node given to be unhidden.
      *        
      * @param targetlevel
      *        Which node to remove, 0 is the bottom node, 1 the parent etc.
      *        
      */
    private boolean unhideAny_(RegexNode root,int targetlevel, Pattern pattern, Character split) {
	boolean hit=false;
	RegexNode child=firstChild.nextSibling;
	while (child!= lastChild & !ignored) { // last child is not a valid child
	    child.marked=false;
	    if (! child.ignored) {
		if (child.unhideAny_(root,targetlevel,pattern,split)) {
		    hit=true;
		};
	    };
	    boolean match = (! child.ignored);
            Integer imatch=child.nodeMatch(root,pattern,split);
            if (match & pattern !=null) match=imatch != null;
 	    if (match) {
		if (debug) System.out.format("Found match: %s\n",child.getIdentification());
		// mark parent
		RegexNode childNode=child;
		int level =0;
		Integer tlevel = targetlevel;
		if (targetlevel < 0) {
		    tlevel = imatch + 1 + targetlevel; // count from top, -1 being top level in node match
		}
		while (level < tlevel & childNode != null & childNode != root){
		    level=level+1;
		    childNode=childNode.parentNode;
		}
		if (level == tlevel & childNode != null) {
		    if (debug) System.out.format("Marked for unhide: %s\n",childNode.getIdentification());
		    childNode.marked=true;
		}
	    }
	    if (child.marked) { // delayed processing...
		if (debug) System.out.format("Unhiding %s\n",child.getIdentification());
		try {
                    child=child.unhide_(root,null,null); // child always has a parent, so the return value can not be null
                } catch (NullPointerException e) {
                    System.out.format("Error unhiding %s\n%s\n",child.identification, toString());
                    throw e;
                }
		hit=true;
	    } else {
		child=child.nextSibling;
	    }
	}
	return hit;
    }
    public RegexNode unhide_(RegexNode root, Pattern pattern, Character split) {
	//check();
	if (parentNode == null) {return null;}; // top node
	boolean match = (! ignored);
	if (match & pattern !=null) match=nodeMatch(root,pattern,split) != null;
	if (match) {
	    Plan plan=planReplacement(getText(),parentNodeStartIndex,parentNodeEndIndex);
	    //System.out.format("Unhiding: %s\n%s\n",plan.toString(),parentNode.toString());
	    //check();
	    if (parentNodeStartIndex < 0 || parentNodeEndIndex >  parentNode.resultText.length()) {
		throw new IllegalStateException(String.format("Invalid child index %s\nId:%d %d\n",parentNode.toString(),identification,parentNode.identification));
	    }
	    parentNode.swap_(plan);
	    shiftSiblingChainIndexes(plan);
	    parentNode.shiftMatchIndexes(plan);
	    return switchWithChildren(); // move indexes in the child to the parent and unlink
	} else {
	    return nextSibling;
	}
    }
    public String getTextAll_() {
	//return resulting string
	// loop over replacements, starting with lowest
	String res="";
	String tt=this.getText();
	int pos=0;
	RegexNode child=this.firstChild.nextSibling;
	while (child!=this.lastChild) {
	    int ss=child.parentNodeStartIndex;
	    int ee=child.parentNodeEndIndex;
	    String cc = child.getTextAll_();
	    res=res + tt.substring(pos,ss) + cc;
	    pos=ee;
	    child=child.nextSibling;
	}
	res=res + tt.substring(pos);
	return res;
    }
    public RegexNode unfold_(String slabel,String elabel) { // unfold this node 
	if (startFoldNode != null || endFoldNode != null) { // || parentNode == null
	    return nextSibling;
	}
	if (slabel==null) {
	    slabel=getLabel();
	}
	setAttribute("labelFolded",getLabel());
	RegexNode e=new RegexNode("");
	e.setNodeName(node+"_");
	e.setAttribute("matches",getIdentification());
	int startIndex=parentNodeStartIndex;
	endFoldNode=e;
	e.startFoldNode=this;
	appendSibling_(endFoldNode,elabel);
	RegexNode next=unhide_(this,null,null); // this node may contain attributes etc...
	useOriginalString("");
	e.parentNode.insertChild(this,elabel,startIndex,startIndex);
	setLabel(slabel);
	return next;
    }
    private boolean unfoldParent_(String slabel, String elabel, int targetlevel, RegexNode root,Pattern pattern, Character split) {
	unfold=false;
	boolean match = (! ignored);
	Integer imatch=nodeMatch(root,pattern,split);
	if (match & pattern !=null) match= imatch != null;
	if (match) {
	    RegexNode targetNode=this;
	    int level =0;
	    Integer tlevel = targetlevel;
	    if (targetlevel < 0) {
		tlevel = imatch + 1 + targetlevel; // count from top, -1 being top level in node match
	    }
	    while (level < tlevel & targetNode != null & targetNode != root){
		level=level+1;
		targetNode=targetNode.parentNode;
	    }
	    if (level == tlevel & targetNode != null) {
		Boolean unfoldable=(Boolean) targetNode.getAttribute("unfoldable");
		if (targetNode.startFoldNode == null & 
		    targetNode.endFoldNode == null &
		    unfoldable == null) unfold=true;
	    }
	    if (unfold) {
		try {
		    targetNode.unfold_(slabel,elabel);
		} catch (NullPointerException e) {
		    System.out.format("Error unfoldParent %s\n%s\n",targetNode.identification, targetNode.toString());
		    throw e;
		}
	    }
	}
	return unfold;
    }

    public boolean unfoldAll_(String slabel, String elabel, int targetlevel, RegexNode root, Pattern pattern, Character split) { // unfold nodes
	if(debug) System.out.format("Entering unfoldall %s\n",getNodeName());
	System.out.format("Entering unfoldall %s\n",getNodeName());
	boolean ret=false;
	RegexNode child=firstChild.nextSibling;
	while (child != lastChild & !ignored) { // last child is not a valid child
	    child.unfold=false;
	    if (! child.ignored) {
		if (child.unfoldAll_(slabel,elabel,targetlevel,root,pattern,split)) {
		    ret=true;
		}
	    };
	    boolean match = (! child.ignored);
	    Integer imatch=child.nodeMatch(root,pattern,split);
	    if (match & pattern !=null) match= imatch != null;
	    if(debug) System.out.format("Matched %s child %s %b %d\n",getNodeName(),child.getNodeName(),match,imatch);
	    if (match) {
		// mark parent
		RegexNode targetNode=child;
		int level =0;
		Integer tlevel = targetlevel;
		if (targetlevel < 0) {
		    tlevel = imatch + 1 + targetlevel; // count from top, -1 being top level in node match
		}
		while (level < tlevel & targetNode != null & targetNode != root){
		    level=level+1;
		    targetNode=targetNode.parentNode;
		}
		if(debug) System.out.format("Requesting unfold level %d %d\n",level,tlevel);
		if (level == tlevel & targetNode != null) {
		    if(debug) System.out.format("Requesting unfold %s\n",targetNode.getIdentification());
		    targetNode.unfold=true;
		}
	    }
	    if (child.unfold) { // delayed processing...
		if(debug) System.out.format("Unfolding %s\n",child.getIdentification());
		//check();
		try {
		    child=child.unfold_(slabel,elabel); // child always has a parent, so the return value can not be null
		} catch (NullPointerException e) {
		    System.out.format("Error unfoldAll %s\n%s\n",child.identification, child.toString());
		    throw e;
		}
	    } else {
		child=child.nextSibling;
	    }
	}
	return ret;
    }
    public RegexNode fold_(String label) { // fold this node up, and return the original node
	RegexNode parent= parentNode;
	int startIndex=0;
	int endIndex=0;
	RegexNode sNode=this;
	RegexNode eNode=this;
	if (endFoldNode != null) {                      // this is a start-node
	    eNode=endFoldNode;
	} else if (startFoldNode != null) {             // this is an end-node
	    sNode=startFoldNode;
	};
	if (sNode == null || eNode == null || sNode == eNode) {
	    System.out.format("###### Invalid fold-node %d# found, not folding.%s\n",
	    		      identification,parentNode.toString());
	    return nextSibling;
	} else if (sNode.parentNode != eNode.parentNode || sNode.parentNode != parentNode) {
	    System.out.format("Stray fold-nodes found, not folding %s.\n",getIdentification());
	    sNode.setAttribute("unfoldable",true);
	    eNode.setAttribute("unfoldable",true);
	    System.out.format("invalid Fold-structure: %s\n",parentNode.toString());
	    // write out interesting part of the tree
	    RegexNode pNode=null;
	    String s=sNode.getIdentification();
	    RegexNode cNode=sNode;
	    while (cNode != null) {
		cNode.setAttribute("foldMismatch",sNode);
		cNode=cNode.getParent();
	    }
	    cNode=eNode;
	    while (cNode != null) {
		RegexNode tNode=(RegexNode) cNode.getAttribute("foldMismatch");
		if (tNode == sNode & pNode == null) {
		    pNode=cNode;
		}
		cNode=cNode.getParent();
	    }
	    if (pNode != null) {
		System.out.format("%s",pNode.toString(sNode,eNode));
	    }
	    throw new IllegalStateException(String.format("Invalid Folding-state: %s.",getIdentification()));
	    //return nextSibling;
	}
	if (label == null) {
	    label=(String) sNode.getAttribute("labelFolded");
	    sNode.removeAttribute("labelFolded");
	}
	sNode.setLabel_("");
	startIndex=sNode.parentNodeEndIndex;
	endIndex=eNode.parentNodeStartIndex;
	RegexNode p=sNode.parentNode;
	sNode.unlink();
	RegexNode child=p.hide_(sNode.node,label,startIndex,endIndex,sNode);
	eNode.rmNode("");
	startFoldNode=null;
	endFoldNode=null;
	removeAttribute("unfoldable");
	return sNode; // re-check this node (in case its children need to bee folded)
    }
    private boolean foldAll_(String label, RegexNode root,Pattern pattern, Character split) { // fold matches
	boolean hit=false;
	RegexNode child=firstChild.nextSibling;
	while (child != null & child != lastChild & !ignored) { // last child is not a valid child
	    if (! child.ignored) {
		if (child.foldAll_(label,root,pattern,split)) {
		    hit=true;
		};
	    };
	    boolean match = (! child.ignored);
	    if (match & pattern !=null) match=child.nodeMatch(root,pattern,split) != null;
	    if (match) match=(child.endFoldNode != null || child.startFoldNode != null);
	    if (match) {
		//check();
		Boolean unfoldable = (Boolean) child.getAttribute("unfoldable");
		if (unfoldable==null) {
		    try {
			child=child.fold_(label); // child always has a parent, so the return value can not be null
		    } catch (NullPointerException e) {
			System.out.format("Error folding %s\n%s\n",child.identification, toString());
			throw e;
		    }
		    hit=true;
		} else {
		    child=child.nextSibling;
		    hit=false;
		}
	    } else {
		child=child.nextSibling;
	    }
	}
	return hit;
    }

    private boolean foldPaired_(String label, RegexNode root,Pattern pattern, Character split) { // fold matches
	boolean hit=false;
	RegexNode child=firstChild.nextSibling;
	while (child != null & child != lastChild & !ignored) { // last child is not a valid child
	    if (! child.ignored) {
		if (child.foldPaired_(label,root,pattern,split)) {
		    hit=true;
		};
	    };
	    boolean match = (! child.ignored);
	    if (match & pattern !=null) match=child.nodeMatch(root,pattern,split) != null;
	    if (match) match=(child.endFoldNode != null || child.startFoldNode != null);
	    if (match) {
		RegexNode sNode=child;
		RegexNode eNode=child;
		if (child.endFoldNode != null) {                      // this is a start-node
		    eNode=child.endFoldNode;
		} else if (child.startFoldNode != null) {             // this is an end-node
		    sNode=child.startFoldNode;
		};
		if (sNode.parentNode == eNode.parentNode & sNode.parentNode == child.parentNode) {
		    try {
			child=child.fold_(label); // child always has a parent, so the return value can not be null
		    } catch (NullPointerException e) {
			System.out.format("Error foldPairing %s\n%s\n",child.identification, toString());
			throw e;
		    }
		    hit=true;
		} else {
		    if (debug) {
			System.out.format("fold parent mismatch (%s#%s->",sNode.getIdentification(),sNode.getNodeName());
			if (sNode.parentNode != null) System.out.format("%s#%s",sNode.parentNode.getIdentification(),sNode.parentNode.getNodeName());
			System.out.format(", %s#%s->",eNode.getIdentification(),eNode.getNodeName());
			if (eNode.parentNode != null) System.out.format("%s#%s",eNode.parentNode.getIdentification(),eNode.parentNode.getNodeName());
			System.out.format(")\n");
		    }
		    child=child.nextSibling;
		    hit=false;
		}
	    } else {
		child=child.nextSibling;
	    }
	}
	return hit;
    }

    private boolean replaceAll_(RegexNode root,String patternText, String replacementText, int group, int subLevel, 
				Pattern pattern,Character split) { // replace current match with processed replacement
	//System.out.format("Replace all %s %s\n",identification,patternText);
	boolean hit=false;
	if (subLevel != 0  & ! ignored) {
	    RegexNode child=firstChild.nextSibling;
	    while (child != lastChild) { // last child is not a valid child
		boolean childhit=child.replaceAll_(root,patternText, replacementText, group, subLevel-1,pattern,split);
		if (childhit){hit=childhit;};
		child=child.nextSibling;
		//System.out.format("Looping hideAll: %s\n",identification);
	    };
	};
	boolean match = (! ignored);
	if (match & this.node!=null & pattern!=null) match=nodeMatch(root,pattern,split) != null;
	if (match) {
	    //System.out.format("Replace all Match %s %s\n",identification,this.node);
	    while (seek_(patternText)) {
		//System.out.format("Replace all FOUND PATTERN %s %s\n",identification,patternText);
		replace(replacementText,group);
		hit=true;
	    }
	}
	return hit;
    }
    private boolean seek_(String patternText) {
	boolean doinit=false;
	if (!doinit) doinit=pattern == null;
	if (!doinit) doinit=oldPatternString == null;
	if (!doinit) doinit=! oldPatternString.toString().equals(patternText);
	if (doinit) {
	    useOriginalString(getText());
	    pattern=Pattern.compile(patternText);
	    matcher=pattern.matcher(originalText);
	    oldPatternString=new StringBuffer(patternText);
	    if(debug) System.out.format("seek Initialising \"%s\" %d\n",pattern.pattern(),matchOffset);
	};
	if(debug) System.out.format("seek using pattern: \"%s\" %s\n",patternText,toString());
	boolean result=seek_();
	return result;
    }
    private boolean seek_() {
	boolean result=matcher.find();
	if (result) {
	    matchResultChanged=false;
	    nMatchGroups=matcher.groupCount();
	    for (Integer ii=0;ii<=nMatchGroups;ii++) {
		int s=matcher.start(ii);
		int e=matcher.end(ii);
		if (s==-1 & e==-1) {
		    matchStartIndexShifted.put(ii,matcher.start(ii));
		    matchEndIndexShifted.put(ii,matcher.end(ii));
		    matchStartIndexOriginal.put(ii,matcher.start(ii));
		    matchEndIndexOriginal.put(ii,matcher.end(ii));
		    matchGroups.put(ii,matcher.group(ii));
		} else {
		    matchStartIndexShifted.put(ii,matcher.start(ii)+matchOffset);
		    matchEndIndexShifted.put(ii,matcher.end(ii)+matchOffset);
		    matchStartIndexOriginal.put(ii,matcher.start(ii));
		    matchEndIndexOriginal.put(ii,matcher.end(ii));
		    matchGroups.put(ii,matcher.group(ii));
		}
	    }
	    matchPos=end(0);
	    if(debug) System.out.format("seek Found match %s id=%d (%d %d) 0(%d %d)\n",pattern.pattern(),identification,matcher.start(0),matcher.end(0),startOriginal(0),endOriginal(0));
	    //System.out.format("Found match %s %d(%d %d) \"%s\"\n",pattern.pattern(),identification,
	    // 		      matcher.start(0),
	    //		      matcher.end(0),,
	    //		      originalText);
	} else {
	    if(debug) System.out.format("seek No match %s %d\n",pattern.pattern(),identification);
	    //System.out.format("seek_ No match %s %d\n",pattern.pattern(),identification);
	    oldPatternString=null;
	};
	return result;
    }
    // concatenate two String arrays
    static public String[] concat(String[] A, String[] B) {
	int aLen = A.length;
	int bLen = B.length;
	String[] C= new String[aLen+bLen];
	System.arraycopy(A, 0, C, 0, aLen);
	System.arraycopy(B, 0, C, aLen, bLen);
	return C;
    }
    static public String[] concat(String A, String[] B) {
	int aLen = 1;
	int bLen = B.length;
	String[] C= new String[aLen+bLen];
	C[0]=A;
	System.arraycopy(B, 0, C, aLen, bLen);
	return C;
    }
    public void dumpParentToFile(String prefile) {
	RegexNode p = this;
	while (p.parentNode != null) {
	    p = p.parentNode;
	}
	p.dumpToFile(prefile);
    }
    public void dumpToFile() {
	dumpToFile(String.format("   dump%d.tree",filecnt++));
    }
    public void dumpToFile(String prefile) {
	String prefix=prefile;
	String fileName=prefile;
	prefix=prefix.replaceAll("\\S*$", "");
	fileName=fileName.replaceAll("^\\s*", "");
	fileName=String.format(fileName,filecnt++);
	FileOutputStream out=null;
	try{
	    System.out.format("%sOutput file: %s\n",prefix,fileName);
	    out = new FileOutputStream(fileName);
	    byte[] contentInBytes = toString().getBytes();
	    out.write(contentInBytes);
	    out.close();
	} catch (IOException e) {
	    System.out.format("Error writing file:%s",fileName);
	    e.printStackTrace();
	} finally {
	    try {
		if (out != null) {
		    out.close();
		}
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	}
    }
    public void writeToFile(String fileName) {
	FileOutputStream out=null;
	try{
	    out = new FileOutputStream(fileName);
	    byte[] contentInBytes = getText().getBytes();
	    out.write(contentInBytes);
	    out.close();
	} catch (IOException e) {
	    e.printStackTrace();
	} finally {
	    try {
		if (out != null) {
		    out.close();
		}
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	}
    }
    private int occurences(String str, Character delim) {
	int num=0; for(int i=0;i<str.length();num+=(str.charAt(i++)==delim?1:0));
	return num;
    }
    // Prepare string with special characters for use in pattern
    private String iron(String s) {
	s=s.replace("(", "\\(");
	s=s.replace(")", "\\)");
	s=s.replace("+", "\\+");
	s=s.replace("-", "\\-");
	s=s.replace("*", "\\*");
	s=s.replace(".", "\\.");
	s=s.replace("{", "\\{");
	s=s.replace("}", "\\}");
	s=s.replace("¤", ".");
	return s;
    }
}
