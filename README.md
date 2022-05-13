# RegexNode
Un-tangled REGEX pattern matching

Have you ever wanted to "hide" your comments from a regular expression pattern matching search, 
and be able to "unhide" your comments afterwards? This JAVA library takes you even further. 

The basic ideas are demonstrated in the examples and summarised below.

Source-code: RegexNode.java

Examples: Example1.java Example2.java Example3.java Example4.java Example5.java Example6.java Example7.java

# `RegexNode`

## Introduction

A _regex_, or regular expression, is a pattern used to locate matches in a _string_. 
A simple _regex_ could for instance be "abc", which would match any occurrence of the substring "abc" in a _string_.
For example, the _regex_ "abc" gives exactly one match in the _string_ 

       "abc efg stu xyz"

A _regex_ may contain special sequences like `\s` for white space and `\w` for word characters.
A more advanced _regex_ with special sequences could be `\s\w{3}\s` which matches three word 
characters with both a leading and a trailing white space. This pattern will only match ` efg ` and ` stu `
in the above string, as `abc` and `xyz` are missing a leading or trailing white space.

Patterns can further be combined into more complicated patterns, for instance 
`/^(?=^abc)(?=.*xyz$)(?=.*efg)(?=^(?:(?!stu).)*$).*$/`  matches strings that start with `abc` and 
end with `xyz` and contains `efg` but does **not** contain `stu`.

The first problem with _regex_ expressions is that the syntax has to be very compact and it is therefore somewhat cryptical.
The second problem is that when _regex_ patterns are combined into a more general _regex_ pattern, they tend to 
become increasingly complicated and difficult to maintain.

The purpose of `RegexNode` is to simplify the processing of complicated regex by breaking the string into a tree structure.
The user can then customize which parts of the tree structure to manipulate.


## Creating a "Hello world" `RegexNode`

The `RegexNode` library is written in `java`. You may create a `RegexNode` using

      	RegexNode regex=new RegexNode("Hello World");

You may retrieve the `Hello World` string using `getText()`, for instance

	String result = regex.getText();      // "Hello World"

Each `RegexNode` has a name which can be set using `setNodeName()`, for instance

	regex.setNodeName("helloworld");

Next we define some _labels_ to use in the parent text, instead of the text contained in the child nodes

	RegexNode.define("<hello>","HHH");
	RegexNode.define("<world>");

The first _label_ named `"<hello>"` has the value `"HHH"`, while the second _label_ named `"<world>"` has a binary value set by the system.
The binary value will not appear naturally in a normal text.

Let us create child nodes of text that matches the _regex_ `H\w*` or `W\w*`

	regex.node("hhh").label("<hello>").hideAll("H\\w*");
	regex.node("www").label("<world>").hideAll("W(\\w*)");

The default processing node name is set using the function `node()`.
The default processing path is `"*"` which simply means 'any node'. 
You may also use the path `"..."` meaning one or more nodes and `"$"` meaning the top of the node tree. 
The default path can be changed with the function `path()`, for instance `path("$","helloworld","...","text")`.

Note the group captured above in the _regex_ `W(\\w*)`, we may create a node for this group using

	regex.node("text").label("plain text").path("www").hideNodeGroup(1);

You may ignore a node using the functions `ignoreAll("hhh")` and later `unignoreAll()`. 
Ignored nodes are not processed.

The `RegexNode` tree can be visualized using `toString()`, which in our case will yield a string like this:

       0#helloworld# "[§] [WWW]" Root [0] 1 groups "orld"(3,7)            
                      1 1 2   2
         1#hhh# "Hello" => 0#(0 1) #i <-99 1 2> no groups     
                      
         2#www# "W[plain text]" => 0#(2 5) <1 2 -99> -1 groups     
                  3          3
           3#text# "orld" => 2#(1 11) <-99 3 -99> no groups      
                 

We observe that the text in the root node (named "helloworld") contains a white space and reference to two children.
The child named "hhh" contains the text "Hello" and is ignored, while the child "www" contains a branch with the text "World".

We may further manipulate by for instance `regex.replaceAll("o","X");` which yields `WXrld` in the `www`-branch.

Finally use `unhideAll()` to retrieve the resulting string, for instance

       regex.path("*").unhideAll();
       System.out.format("%s\n", regex.getText());

gives the result `Hello WXrld` as expected.

## Example 1
This example demonstrates use of "hideAll", "ignoreAll" and "replaceAll".

    public class Example1 {
     public static void main(String[] args) {

        // RegexNode example by Frank Thomas Tveter 2016.
        // This example demonstrates use of "hideAll", "ignoreAll" and "replaceAll".
        
        // initialise
        RegexNode regex=new RegexNode("foo foo foo <comment with foo> foo foo <more comments with foo>");
        regex.node("Groot").path("*"); // set the name of the root-node
        System.out.format("Initial text:'%s'\n\n", regex.getText());

        // define non-ASCII label character (not originally in the text)
        RegexNode.define("<Comment>");

        // hide comments in "comment"-node with non-ASCII label "<Comment>" in the "Groot" node
        //regex.hideAll( "comment", "<.*?>", "<Comment>","*"); // non-gready search
        regex.node("comment").label("<Comment>").hideAll("<.*?>"); // non-gready search
        regex.ignoreAll("comment"); // do not process "comment"-nodes later on
        System.out.format("Tree with comments hidden:%s\n", regex.toString());

        // change "foo" to "bar"
        regex.replaceAll("foo","bar"); // replace all "foo" by "bar" in all (not-ignored) nodes
        System.out.format("Current Text:'%s'\n", regex.getTextAll());

        // replace "foo" by "tar" in the comments
        regex.unignoreAll("*"); // do not ignore any nodes any more
        regex.path("comment").replaceAll("foo","tar"); // replace "foo" by "tar" in all "comment"-nodes

        // retrieve the final text
        regex.path("*").unhideAll(); // un-hide all nodes
        System.out.format("Final text:  '%s'\n", regex.getText());
     }
    }

The program gives the following output.

    Initial text:'foo foo foo <comment with foo> foo foo <more comments with foo>'

    Tree with comments hidden:
    0#Groot# "foo foo foo [§] foo foo [§]" Root [0] no groups       
                       1 1         2 2
      1#comment# "<comment with foo>" => 0#(12 13) #i <-99 1 2> no groups         
                                 
      2#comment# "<more comments with foo>" => 0#(22 23) #i <1 2 -99> no groups         
                                      
    #### Total number of nodes defined = 2.

    Final text:'bar bar bar <comment with tar> bar bar <more comments with tar>'

## Example 2
 This example demonstrates how to use "getNode" to loop through nodes.

    public class Example2 {
     public static void main(String[] args) {

        // RegexNode example by Frank Thomas Tveter 2016.
        // This example demonstrates how to use "getNode" to loop through nodes.
        
        // initialise
        RegexNode regex=new RegexNode("foo foo foo <comment with foo> foo foo <more comments with foo>");
        regex.setNodeName("Groot");
        System.out.format("Initial text:'%s'\n\n", regex.getText());

        // define non-ASCII label character (
        RegexNode.define("<Comment>");

        // hide comments in non-ASCII label "<Comment>"
        regex.hideAll( "comment", "<.*?>", "<Comment>","*"); // non-greedy search
        System.out.format("Tree with comments hidden:%s\n", regex.toString());

        // getNodeReset("comment"); // reset "getNode" in case it was used in a previous loop that ended prematurely

        // loop through comment nodes...
        RegexNode commentNode=regex.getNode("comment");
        while (commentNode != null) {
            RegexNode nextNode=regex.getNode("comment"); // use this in case we want to delete/move commentNode
            System.out.format("Found comment:%s\n",commentNode.getText());
            commentNode=nextNode;
        }

        // retrieve the final text
        regex.unignoreAll();
        regex.unhideAll();
        System.out.format("\nFinal text:'%s'\n", regex.getText());
     }
    }

This program gives the following output.

    Initial text:'foo foo foo <comment with foo> foo foo <more comments with foo>'

    Tree with comments hidden:
    0#Groot# "foo foo foo [§] foo foo [§]" Root [0] no groups       
                          1 1         2 2
      1#comment# "<comment with foo>" => 0#(12 13) <-99 1 2> no groups         
                                   
      2#comment# "<more comments with foo>" => 0#(22 23) <1 2 -99> no groups         
                                      
    #### Total number of nodes defined = 2.

    Found comment:<comment with foo>
    Found comment:<more comments with foo>

    Final text:'foo foo foo <comment with foo> foo foo <more comments with foo>'

## Example 3
This example demonstrates how to move nodes around in the node tree.

    public class Example3 {
     public static void main(String[] args) {

        // RegexNode example by Frank Thomas Tveter 2016.
        // this example demonstrates how to move nodes around in the node tree
        
        // initialise
        RegexNode regex=new RegexNode("foo foo foo <comment with foo> foo foo <more comments with foo>");
        regex.setNodeName("Groot");
        System.out.format("Initial text:'%s'\n\n", regex.getText());

        // define non-ASCII label character (
        RegexNode.define("<Comment>");

        // hide comments in non-ASCII label "<Comment>"
        regex.hideAll( "comment", "<.*?>", "<Comment>","*"); // non-gready search
        System.out.format("Tree with comments hidden:%s\n", regex.toString());

        // first and last comments
        RegexNode firstNode=null;
        RegexNode lastNode=null;

        // loop through comment nodes, find first and last comment nodes...
        RegexNode commentNode=regex.getNode("comment");
        while (commentNode != null) {
            RegexNode nextNode=regex.getNode("comment"); // use this in case we want to delete/move commentNode
            System.out.format("Found comment:%s\n",commentNode.getText());
            if (firstNode == null) { firstNode=commentNode;};
            lastNode=commentNode;
            commentNode=nextNode;
        }

        // swap first and last comment-nodes
        if (firstNode != null && lastNode != null) {
            RegexNode buffNode=new RegexNode("");
            buffNode.replace(firstNode);
            firstNode.replace(lastNode);
            lastNode.replace(buffNode);
            System.out.format("\nTree with comments swapped:%s\n", regex.toString());
        }

        // retrieve the final text
        regex.unignoreAll();
        regex.unhideAll();
        System.out.format("Final text:'%s'\n", regex.getText());
     }
    }

This program gives the following output.


    Initial text:'foo foo foo <comment with foo> foo foo <more comments with foo>'

    Tree with comments hidden:
    0#Groot# "foo foo foo [§] foo foo [§]" Root [0] no groups       
                          1 1         2 2
      1#comment# "<comment with foo>" => 0#(12 13) <-99 1 2> no groups         
                                
      2#comment# "<more comments with foo>" => 0#(22 23) <1 2 -99> no groups         
                                      
    #### Total number of nodes defined = 2.

    Found comment:<comment with foo>
    Found comment:<more comments with foo>

    Tree with comments swapped:
    0#Groot# "foo foo foo [§] foo foo [§]" Root [0] no groups       
                          2 2         1 1
      2#comment# "<more comments with foo>" => 0#(12 13) <-99 2 1> no groups         
                                      
      1#comment# "<comment with foo>" => 0#(22 23) <2 1 -99> no groups         
                                
     #### Total number of nodes defined = 3.

     Final text:'foo foo foo <more comments with foo> foo foo <comment with foo>'

## Example 4
This example demonstrates how to create a RegexNode tree structure directly.

    public class Example4 {
     public static void main(String[] args) {

        // RegexNode example by Frank Thomas Tveter 2016.
        // this example demonstrates how to create a RegexNode tree structure directly

        RegexNode regex=new RegexNode("Groot:foo foo foo ¤ foo foo ¤;"
                                          + "comment:<comment with foo>;"
                                          + "comment:<more comments with foo>;",
                                          ':',';','¤');

        // print the tree
        System.out.format("Tree:%s\n", regex.toString());

        // retreive the final text
        regex.unhideAll(); 
        System.out.format("Final text:'%s'\n", regex.getText());
     }
    }

This program gives the following output.

    Tree:
    0#Groot# "foo foo foo [¤] foo foo [¤]" Root [0] -1 groups       
                          1 1         2 2
      1#comment# "<comment with foo>" => 0#(12 13) <-99 1 2> -1 groups         
                                 
      2#comment# "<more comments with foo>" => 0#(22 23) <1 2 -99> -1 groups         
                                       
    #### Total number of nodes defined = 2.
   
    Final text:'foo foo foo <comment with foo> foo foo <more comments with foo>'
    
## Example 5
This example demonstrates how to "unfold" and "fold" nodes, temporarily "unhiding" nodes.

    public class Example5 {
     public static void main(String[] args) {

        // RegexNode example by Frank Thomas Tveter 2016.
        // this example demonstrates how to "unfold" and "fold" nodes, temporarily "unhiding" nodes.

        RegexNode regex=new RegexNode("Groot:foo foo foo ¤ foo foo ¤;"
                                          + "comment:<comment with foo>;"
                                          + "comment:<more comments with foo>;",
                                          ':',';','¤');
        RegexNode.define("<Fold>");

        // print the tree
        System.out.format("Tree:%s\n", regex.toString());

        RegexNode firstNode=regex.getFirstNode("comment");

        firstNode.unfold("<Fold>","<Fold>"); 
        // print the tree
        System.out.format("Tree with an unfolded comment:%s\n", regex.toString());


        // change "foo" to "bar"
        regex.replaceAll("foo","bar","Groot"); // replace all "foo" by "bar" in root node

        // retreive the final text
        regex.foldAll(); 
        regex.unhideAll(); // un-hide all nodes
        System.out.format("Final text:'%s'\n", regex.getText());
     }
    }

This program gives the following output.

    Tree:
    0#Groot# "foo foo foo [¤] foo foo [¤]" Root [0] -1 groups       
                          1 1         2 2
      1#comment# "<comment with foo>" => 0#(12 13) <-99 1 2> -1 groups         
                                    
      2#comment# "<more comments with foo>" => 0#(22 23) <1 2 -99> -1 groups         
                                      
    #### Total number of nodes defined = 2.

    Tree with an unfolded comment:
    0#Groot# "foo foo foo [§]<comment with foo>[§] foo foo [¤]" Root [0] -1 groups       
                          1 1                  3 3         2 2
      1#comment# "" => 0#(12 13) <-99 1 3> -1 groups End3#         
              
         @labelFolded = "¤"
      3#comment_# "" => 0#(31 32) <1 3 2> -1 groups Start1#          
               
         @matches = "1"
      2#comment# "<more comments with foo>" => 0#(41 42) <3 2 -99> -1 groups         
                                      
    #### Total number of nodes defined = 3.

    Final text:'bar bar bar <comment with bar> bar bar <more comments with foo>'

## Example 6
This example demonstrates how to hide groups in a pattern using hideall and hideNodeGroup.

    public class Example6 {
     public static void main(String[] args) {

        // RegexNode example by Frank Thomas Tveter 2016.
        // This example demonstrates how to hide groups in a pattern
        // using hideall and hideNodeGroup.
        
        RegexNode regex=new RegexNode("Groot:foo foo foo ¤ foo foo ¤;"
                                          + "comment:<comment with foo>;"
                                          + "comment:<more comments with foo>;",
                                          ':',';','¤');

        // define non-ASCII label character (not originally in the text)
        RegexNode.define("<Group>");

        regex.hideAll( "foo", "(f+)(o+)", "<Group>","Groot"); // non-gready search, only on "Groot"-node
        regex.hideNodeGroup("group1","X",1,"Groot","foo");
        regex.hideNodeGroup("group2","Z",2,"Groot","foo");

        System.out.format("Tree with groups:%s\n", regex.toString());
     }
    }

This program gives the following output.

    Tree with groups:
    0#Groot# "[§] [§] [§] [¤] [§] [§] [¤]" Root [0] 2 groups "f"(10,11) "oo"(11,13)       
              3 3 4 4 5 5 1 1 6 6 7 7 2 2
      3#foo# "[X][ Z] " => 0#(0 1) <-99 3 4> 2 groups "f"(0,1) "oo"(1,2)     
              8 813 13
        8#group1# "f" => 3#(0 1) <-99 8 13> no groups        
                   
        13#group2# "oo" => 3#(1 2) <8 13 -99> no groups         
                     
      4#foo# "[X][ Z] " => 0#(2 3) <3 4 5> 2 groups "f"(0,1) "oo"(1,2)     
              9 914 14
        9#group1# "f" => 4#(0 1) <-99 9 14> no groups        
                   
        14#group2# "oo" => 4#(1 2) <9 14 -99> no groups         
                     
      5#foo# "[ X] [ Z] " => 0#(4 5) <4 5 1> 2 groups "f"(0,1) "oo"(1,2)     
              10 1015 15
        10#group1# "f" => 5#(0 1) <-99 10 15> no groups         
                    
        15#group2# "oo" => 5#(1 2) <10 15 -99> no groups         
                     
      1#comment# "<comment with foo>" => 0#(6 7) <5 1 6> -1 groups         
                                   
      6#foo# "[ X] [ Z] " => 0#(8 9) <1 6 7> 2 groups "f"(0,1) "oo"(1,2)     
              11 1116 16
        11#group1# "f" => 6#(0 1) <-99 11 16> no groups         
                    
        16#group2# "oo" => 6#(1 2) <11 16 -99> no groups         
                     
      7#foo# "[ X] [ Z] " => 0#(10 11) <6 7 2> 2 groups "f"(0,1) "oo"(1,2)     
              12 1217 17
        12#group1# "f" => 7#(0 1) <-99 12 17> no groups         
                    
        17#group2# "oo" => 7#(1 2) <12 17 -99> no groups         
                     
      2#comment# "<more comments with foo>" => 0#(12 13) <7 2 -99> -1 groups         
                                         
    #### Total number of nodes defined = 17.

## Example 7
This example demonstrates how to make a map to the node-strings.

    public class Example7 {
     public static void main(String[] args) {

        // RegexNode example by Frank Thomas Tveter 2016.
        // This example demonstrates how to make a map to the node-strings.
        
        RegexNode regex=new RegexNode("Groot:foo foo foo ¤ foo foo ¤;"
                                          + "comment:<comment with foo>;"
                                          + "comment:<more comments with foo>;",
                                          ':',';','¤');

        RegexNode.define("<Word>");
        regex.hideAll( "word", "\\w+", "<Word>","comment"); // non-gready search

        // print the tree
        System.out.format("Tree:%s\n", regex.toString());

        // make the node-string map
        HashMap<String,ArrayList<RegexNode>> wordMap = regex.makeMap("comment","word");
        ArrayList<RegexNode> fooList = wordMap.get("foo");

        if (fooList != null) {
            System.out.format("Found %d occurences of 'foo' in the comments.\n\n", fooList.size());
        } else {
            System.out.format("Found no occurences of 'foo' in the comments.\n\n");
        }

        // retrieve the final text
        regex.unhideAll(); // un-hide all nodes
        System.out.format("Final text:'%s'\n", regex.getText());
     }
    }

This program gives the following output.

    Tree:
    0#Groot# "foo foo foo [¤] foo foo [¤]" Root [0] -1 groups       
                          1 1         2 2
      1#comment# "<[§] [§] [§]>" => 0#(12 13) <-99 1 2> no groups         
                   3 3 4 4 5 5 
        3#word# "comment" => 1#(1 2) <-99 3 4> no groups      
                       
        4#word# "with" => 1#(3 4) <3 4 5> no groups      
                    
        5#word# "foo" => 1#(5 6) <4 5 -99> no groups      
                   
      2#comment# "<[§] [§] [§] [§]>" => 0#(22 23) <1 2 -99> no groups         
                   6 6 7 7 8 8 9 9 
        6#word# "more" => 2#(1 2) <-99 6 7> no groups      
                    
        7#word# "comments" => 2#(3 4) <6 7 8> no groups      
                        
        8#word# "with" => 2#(5 6) <7 8 9> no groups      
                    
        9#word# "foo" => 2#(7 8) <8 9 -99> no groups      
                   
    #### Total number of nodes defined = 9.
   
    Found 2 occurences of 'foo' in the comments.
   
    Final text:'foo foo foo <comment with foo> foo foo <more comments with foo>'

## Example 8
This example demonstrates how to hide text that has not yet been hidden.

    public class Example8 {
     public static void main(String[] args) {

        // RegexNode example by Frank Thomas Tveter 2016.
        // This example demonstrates how to hide text that has not yet been hidden.
        
        RegexNode regex=new RegexNode("Groot:foo foo foo ¤ foo foo ¤;"
                                          + "comment:<comment with foo>;"
                                          + "comment:<more comments with foo>;",
                                          ':',';','¤');
        RegexNode.define("<rest>");
        regex.hideTheRest("rest","<rest>");
        System.out.format("Node tree: %s\n", regex.toString());

        // retrieve the final text
        regex.unhideAll(); // un-hide all nodes
        System.out.format("Final text:'%s'\n", regex.getText());
     }
    }
    
This program gives the following output.

    Node tree: 
    0#Groot# "[§][¤][§][¤]" Root [0] -1 groups       
              3 31 14 42 2
      3#rest# "foo foo foo " => 0#(0 1) <-99 3 1> -1 groups      
                       
      1#comment# "<comment with foo>" => 0#(1 2) <3 1 4> -1 groups         
                                
      4#rest# " foo foo " => 0#(2 3) <1 4 2> -1 groups      
                    
      2#comment# "<more comments with foo>" => 0#(3 4) <4 2 -99> -1 groups         
                                      
    #### Total number of nodes defined = 4.

    Final text:'foo foo foo <comment with foo> foo foo <more comments with foo>'

## Functions


### Constructors

        RegexNode(String originalText)
        RegexNode(String originalText, String nodeName)
        RegexNode(String originalText, Character o, Character d)
        RegexNode(String originalText, Character o, Character d, Character a)
        RegexNode(String originalText, Character o, Character d, Character t, RegexNode... nodes)
        RegexNode(String originalText, Character o, Character d, Character a, Character t, RegexNode... nodes)
        RegexNode(RegexNode node)

The `originalText` can be in _tree-format_, for instance 

        RegexNode regex=new RegexNode("helloworld:¤ ¤;hello:Hello;world:World;",':',';','¤');

### Anchors
Anchors are non-ascii characters that are defined and used as labels to make the labels unique. 

        static void define(String anchorName, String anchorValue)
        static void define(String anchorName)

        String replaceAnchorNames(String text)

        boolean replaceLabelAnchorNames()
        boolean replaceLabelAnchorNames(String[] name1, String... name2)
        boolean replaceLabelAnchorNames(String... path)
        boolean replaceLabelAnchorNames_(RegexNode root, int targetlevel, Pattern pattern, Character split)

        String replaceAnchors(String text)

        boolean replaceLabelAnchors()
        boolean replaceLabelAnchors(String[] name1, String... name2)
        boolean replaceLabelAnchors(String... path)
        boolean replaceLabelAnchors_(RegexNode root, int targetlevel, Pattern pattern, Character split)

        static String getAnchor(int id)
        String getAnchorTable()

For example

        RegexNode.define("<world>");

### Set defaults

        RegexNode node(String node)
        RegexNode label(String label)
        RegexNode tag(String tag)
        RegexNode pattern(String pattern)
        RegexNode replacement(String replacement)
        RegexNode path(String... path)
        RegexNode path(String[] name1, String... name2)
        RegexNode assignMark (Character o)
        RegexNode delimiterMark (Character d)
        RegexNode tagMark (Character a)
        RegexNode nodeMark (Character t)
        RegexNode splitMark (Character s)
        RegexNode mark (Character o)
        RegexNode mark (Character o,Character d)
        RegexNode mark (Character o,Character d,Character a)
        RegexNode mark (Character o,Character d,Character a,Character t)

Example:

	regex.label("<world>");

### Convert to and from _tree-format_

        RegexNode decode()
        RegexNode decode(Character o, Character d, Character a)
        RegexNode decode(String code)
        Integer decode(String code,Integer pos)
        String encode()

Example:

	regex.decode("helloworld:¤ ¤;hello:Hello;world:World;",':',';','¤')

### Debugging system

        void debugOn()
        void debugOff()
        void check()
        void thisString()
        String getIdentification()
        int getMaxIdentification()
        Integer getLocationLevel()

### Output
The node tree structure can be displayed using `toString`. 
The text in the node can be retrieved using `getText`.

        String toString()
        String toString(int sublevel)
        String getText()
        String getText(String[] name1, String... name2)
        void dumpParentToFile(String prefile)
        void dumpToFile()
        void dumpToFile(String prefile)
        void writeToFile(String fileName)

Example:

        System.out.format("Node tree: %s\n", regex.toString());
        System.out.format("Node text: %s\n", regex.getText());

### Create child nodes, hide substrings:

If a match, or group within a match, is "hidden" by for instance `hide`, it 
may be replaced by a label which can be empty. The label is available for later matching.
The "hidden" text is assigned a name ("node") so that it can be "unhidden" later for instance
by `unhide`. When text is "unhidden" it replaces the corresponding label again. 

Text that has not been "hidden" yet, can be "hidden" using `hideTheRest()`.  

        boolean hideNode()
        boolean hideNode(String newNode)
        boolean hideNode(String newNode,String label)
        boolean hideNode(String newNode, String label, String[] name1, String... name2)
        boolean hideNode(String newNode, String label, String... path)
        boolean hideNodeGroup()
        boolean hideNodeGroup(String groupNode)
        boolean hideNodeGroup(int group)
        boolean hideNodeGroup(String groupNode, int group)
        boolean hideNodeGroup(String groupNode, String label, int group)
        boolean hideNodeGroup(int group, String[] name1,String... name2)
        boolean hideNodeGroup(String groupNode, int group, String[] name1,String... name2)
        boolean hideNodeGroup(String groupNode, String label, int group, String[] name1,String... name2)
        boolean hideNodeGroup(String groupNode, String label, int group, String... path)
        boolean makeParentAll()
        boolean makeParentAll(String node)
        boolean makeParentAll(String node,String label)
        boolean makeParentAll(String node, String label, String[] name1, String... name2)
        boolean makeParentAll(String node, String label, String... path)
        RegexNode makeParent()
        RegexNode makeParent(String node)
        RegexNode makeParent(String node,String label)
        RegexNode hide()
        RegexNode hide(int group)
        RegexNode hide(String node)
        void hide(String node, int group)
        RegexNode hide(String node, String label)
        RegexNode hide(String node, String label, int group)
        RegexNode hide(String node, String label, int startIndex, int endIndex, RegexNode child)
        RegexNode hideAll()
        RegexNode hideAll(String patternText)
        RegexNode hideAll(String node,String patternText)
        RegexNode hideAll(String node,String patternText,String label)
        RegexNode hideAll(String node, String patternText, String label, 
        boolean hideLoop(String patternText)
        boolean hideLoop(String node, String patternText)
        boolean hideLoop(String node, String patternText, String label)
        boolean hideLoop(String node, String patternText, String label, 
        boolean hideLoop(String node, String patternText, String label, 
        RegexNode hideTheRest()
        RegexNode hideTheRest(String node)
        boolean hideTheRest(String node, String label)
        boolean hideTheRestAll()
        boolean hideTheRestAll(String node)
        boolean hideTheRestAll(String node,String label)
        boolean hideTheRestAll(String[] path)
        boolean hideTheRestAll(String node, String label, String[] name1, String... name2)
        boolean hideTheRestAll(String node, String label, String... path)

        RegexNode unhide()
        RegexNode unhide(String[] name1, String... name2)
        RegexNode unhide(String... path)
        boolean unhideAll()
        boolean unhideAll(String[] name1, String... name2)
        boolean unhideAll(String... path)
        boolean unhideAll(Integer level)
        boolean unhideAll(Integer level, String[] name1, String... name2)
        boolean unhideAll(Integer level, String... path)

Example:

        regex.hideAll( "www", "W(\\w*)","<world>","$","helloworld");
        regex.node("www").label("<world>").path("$","helloworld").hideAll("W(\\w*)");
        regex.unhideAll("*");

### Find child nodes

        RegexNode getFirstNode()
        RegexNode getFirstNode(String[] name1, String... name2)

        Integer getFirstLength()
        Integer getFirstLength(String[] name1, String... name2)
        Integer getFirstLength(String... path)

        boolean find()
        boolean find(String patternText)

        void resetSeek()

        boolean seek()
        boolean seek(String patternText)

        boolean seekAll()
        boolean seekAll(String patternText)
        boolean seekAll(String patternText, String[] name1, String... name2)
        boolean seekAll(String patternText, String... node)

The text can be searched `seek` using normal regex syntax. Matches may be
processed and the original text replaced `replace` following the same syntax. 
Here is an example:

        while (regex.seek("( <world>)")) {
            RegexNode world=regex.hide("wrld","WORLD",1);
            RegexNode wrld=regex.getFirstNode("$","helloworld","*");
            System.out.format("World text: %s\n", wrld.getText());
        };


### Selecting and looping over nodes

If a match is replaced and "hidden", the existing nodes within the match are moved to
the new node. Nodes can in this way be organised in a tree structure. Nodes and their 
child-nodes can be processed seperately from the rest of the node tree. The node tree 
structure may be navigated by looping over nodes with a given name using `getNode`.
The order in which the nodes are put into the tree follows the order they appear in the text.
Existing nodes must be deleted explicitly using `rmnode` or recovered using for 
instance `uhideAll`. Only "hiding" parts of an existing label causes an error. 

        String getNodeName()

        RegexNode getNode()
        RegexNode getNode(String[] name1, String... name2)
        RegexNode getNode(String... path)
        RegexNode getNode(String nodePatternText, Character split)

        ArrayList<RegexNode> getNodeAll()
        ArrayList<RegexNode> getNodeAll(String[] name1, String... name2)
        ArrayList<RegexNode> getNodeAll(String... path)
        ArrayList<RegexNode> getNodeAll(int targetlevel)
        ArrayList<RegexNode> getNodeAll(int targetlevel,String[] name1, String... name2)
        ArrayList<RegexNode> getNodeAll(int targetlevel, String... path)
        ArrayList<RegexNode> getNodeAll(int targetlevel, String nodePatternText, Character split)
        ArrayList<RegexNode> getNodeAll_(RegexNode root, int targetlevel, Pattern pattern, Character split)

        void getNodeReset(String... path)
        RegexNode getNextSibling()
        RegexNode getPrevSibling()
        RegexNode getNextSibling(String name)
        RegexNode getPrevSibling(String name)
        RegexNode getFirstChild()
        RegexNode getLastChild()
        RegexNode getOnlyChild()
        RegexNode getOnlyChild(String nodeName)
        RegexNode getTopChild(RegexNode child)

        boolean contains(RegexNode child)
        RegexNode getParentOf(String name)

        RegexNode getParent()
        RegexNode getParent(int level)
        RegexNode getParent(String[] name1, String... name2)
        RegexNode getParent(String... path)

        int count(String match)
        int count(String match, String[] name1, String... name2)
        int count(String match,String... path)

        Integer countChildren()
        Boolean isBefore(RegexNode sibling)
        Boolean isAfter(RegexNode sibling)
        boolean hasChildren()
        boolean hasStructure()
        boolean hasStructure(String... path)

### Temporarily unfold parts of tree

You may _unfold_ a tree structure so that you may match patterns across several nodes at once. 
Later you may re-fold and re-create the tree structure again.

        boolean isunfolded()
        void unfold(String slabel,String elabel)
        boolean unfoldAll(String slabel,String elabel)
        boolean unfoldAll(String slabel,String elabel,String... path)
        boolean unfoldParentAll(String slabel,String elabel)
        boolean unfoldParentAll(String slabel,String elabel,String... path)
        boolean unfoldParentAll(ArrayList<RegexNode>  nodeList, String slabel, String elabel, String[] name1, String... name2)
        boolean unfoldParentAll(ArrayList<RegexNode>  nodeList, String slabel, String elabel, String... path)
        boolean unfoldParentAll(RegexNode  cNode, String slabel, String elabel)
        boolean unfoldParentAll(RegexNode  cNode, String slabel, String elabel, String[] name1, String... name2)
        boolean unfoldParentAll(RegexNode  cNode, String slabel, String elabel, String... path)
        boolean unfoldParent(RegexNode child, String slabel, String elabel)
        boolean unfoldParent(RegexNode child, String slabel, String elabel, String... path)
        boolean unfoldParent(RegexNode child, String slabel, String elabel, int targetlevel, String... path)
        void fold()
        void fold(String label)
        boolean foldAll()
        boolean foldAll(String label,String... path)
        boolean setLabelUnPaired(String slabel, String elabel, String[] name1, String... name2)
        boolean setLabelUnPaired(String slabel, String elabel, String... path)
        boolean setLabelUnPaired(String slabel, String elabel, int targetlevel, String[] name1, String... name2)
        boolean setLabelUnPaired(String slabel, String elabel, int targetlevel, String... path)
        boolean foldPaired()
        boolean foldPaired(String label,String... path)
        RegexNode addUnFolded(RegexNode startNode, RegexNode endNode)
        RegexNode addUnFolded(String nodeName, String startLabel,
        RegexNode addUnFolded(String nodeName, String startLabel, String endLabel,
        RegexNode addUnFolded(String nodeName, String label, String startLabel, String endLabel,
        RegexNode getEndFoldNode()
        RegexNode getStartFoldNode()

### Copy `RegexNode`

        RegexNode duplicate()
        RegexNode duplicate(RegexNode parentNode)
        void copy(RegexNode blueprint)

### Remove or re-arrange child nodes
        boolean rmNode()
        boolean rmNode(String label)
        boolean rmNodeAll(String label,String[] name1, String... name2)
        boolean rmNodeAll(String label,String... path)
        boolean rmNodeAll_(String label, RegexNode root, int targetlevel, Pattern pattern, Character split)

        RegexNode prependChild(RegexNode child)
        RegexNode prependChild(RegexNode child, String label)
        RegexNode prependSibling(RegexNode sibling)
        RegexNode prependSibling(RegexNode sibling, String label)
        RegexNode prependUnfoldedSibling(RegexNode sibling)
        RegexNode prependUnfoldedSibling(RegexNode sibling, String label)
        RegexNode insertChild(RegexNode child)
        RegexNode insertChild(RegexNode child, String label, int startIndex, int endIndex)
        RegexNode appendChild(RegexNode child, String label, int group)
        RegexNode appendChild(RegexNode child)
        RegexNode appendChild(RegexNode child, String label)
        RegexNode appendSibling(RegexNode sibling)
        RegexNode appendSibling(RegexNode sibling, String label)
        RegexNode appendUnfoldedSibling(RegexNode sibling)
        RegexNode appendUnfoldedSibling(RegexNode sibling, String label)
        RegexNode appendSibling_(RegexNode sibling, String label)

        RegexNode replace(RegexNode victim)

        Plan replace()
        Plan replace(int group)
        Plan replace(String replacementText, int group)
        Plan replace(String replacementText, int startIndex, int endIndex)
        boolean replaceAll()
        boolean replaceAll(String patternText, String replacementText)
        boolean replaceAll(String patternText, String replacementText, String[] name1, String... name2)
        boolean replaceAll(String patternText, String replacementText, String... path)
        boolean replaceAll(int group, int subLevel)
        boolean replaceAll(int group, int subLevel, String[] name1, String... name2)
        boolean replaceAll(String patternText, String replacementText, int subLevel, String... path)
        boolean replaceAll(String patternText, String replacementText, int group, int subLevel, String[] name1, String... name2)
        boolean replaceAll(String patternText, String replacementText, int group, int subLevel, String... path)

### Manipulate the `RegexNode` content text

A plan describes how the text was shifted when substrings were replaced by labels.

        int getParentStartIndex()
        int getParentEndIndex()

        Plan prependText(String text)
        Plan prependText(int len,String text)

        String prependChildLabel(RegexNode child, String mark)
        String appendChildLabel(RegexNode child, String mark)
        Plan prependText(String text,int group)
        Plan prependSiblingText(String text)
        Plan appendSiblingText(String text)
        Plan appendText(String text,int group)
        Plan appendText(String text,RegexNode child)
        Plan prependText(String text,RegexNode child)
        Plan appendText(String text)
        Plan appendText(int len, String text)
        void appendText(String text, String[] name1, String... name2)
        void appendText(String text, String... path)
        void prependText(String text, String[] name1, String... name2)
        void prependText(String text, String... path)

### Change name and label

        RegexNode name(String node)
        boolean setNodeName(String node)
        boolean setNodeNameAll()
        boolean setNodeNameAll(String newnode)
        boolean setNodeNameAll(String newnode, String[] name1, String... name2)
        boolean setNodeNameAll(String newnode, String... path)
        String setLabel(String label)
        String getLabel()
        String setChildLabel(RegexNode child, String label)
        String setChildLabel(RegexNode child, Character c)
        boolean setLabelAll()
        boolean setLabelAll(String label, String[] name1, String... name2)
        boolean setLabelAll(String label, String... path)
        boolean setLabelAll(String label, int targetlevel, String[] name1, String... name2)
        boolean setLabelAll(String label, int targetlevel, String... path)
        boolean setLabelAll(Character label, String[] name1, String... name2)
        boolean setLabelAll(Character label, String... path)
        boolean setLabelAll(Character label, int targetlevel, String[] name1, String... name2)
        boolean setLabelAll(Character label, int targetlevel, String... path)

### Mark label for delayed processing

You may mark labels with a prefex and suffix. If these are null, any earlier marks are removed.

        boolean markLabelAll(String... name2)
        boolean markLabelAll(String startmark, String endmark, String[] name1, String... name2)
        boolean markLabelAll(String startmark, String endmark, String... path)
        boolean markLabelAll(int targetlevel, String[] name1, String... name2)
        boolean markLabelAll(int targetlevel, String... name2)
        boolean markLabelAll(String startmark, int targetlevel, String[] name1, String... name2)
        boolean markLabelAll(String startmark, String endmark, int targetlevel, String[] name1, String... name2)
        boolean markLabelAll(String startmark, String endmark, int targetlevel, String... path)
        boolean markLabelAll(RegexNode child, int targetlevel, String... path)
        boolean markLabelAll(RegexNode child, String startmark, int targetlevel, String... path)
        boolean markLabelAll(RegexNode child, String startmark, String endmark, int targetlevel, String... path)
        boolean markLabelAll(ArrayList<RegexNode>  nodeList, int targetlevel, String[] name1, String... name2)
        boolean markLabelAll(ArrayList<RegexNode>  nodeList, String startmark, int targetlevel, String[] name1, String... name2)
        boolean markLabelAll(ArrayList<RegexNode>  nodeList, String startmark, String endmark, int targetlevel, String[] name1, String... name2)
        boolean markLabelAll(ArrayList<RegexNode>  nodeList, int targetlevel, String... path)
        boolean markLabelAll(ArrayList<RegexNode>  nodeList, String startmark, int targetlevel, String... path)
        boolean markLabelAll(ArrayList<RegexNode>  nodeList, String startmark, String endmark, int targetlevel, String... path)

### Translate text strings

        String translate()
        String translate(String replacementText)

### Ignore parts of the tree
The "ignore"-feature can be used to make only certain nodes visible to the processing.
"Ignorering" is in other words a method to deactivate pattern matching for parts of the 
node tree, for instance if a more general pattern may match already nodeged patterns that 
should not be processed by `hideAll`. Ignored nodes are not processed by 
`hideAll`, `replaceAll` and `unhideAll`. The "ignore" status of 
a node can be retrieved by `getIgnore`. New nodes are be default "unignored". 
Specific nodes can be "ignored" by `ignore`. 

        void ignore()
        RegexNode ignoreAll()
        RegexNode ignoreAll(String[] name1, String... name2)
        RegexNode ignoreAll(String... path)
        void unignore()
        RegexNode unignoreAll()
        RegexNode unignoreAll(String[] name1,String... name2)
        RegexNode unignoreAll(String... path)
        boolean getIgnore(String node)

### Location

The location is the numerical position in the tree.

        void setLocationAll()
        Boolean afterLocation(RegexNode target)
        Boolean beforeLocation(RegexNode target)
        Integer matchesLocationLevel(RegexNode target)
        Integer[] getLocation()
        Integer getLocationPosition()

### Attributes

        RegexNode attribute(String att)
        void setAttribute(String attName, Object attObject)
        int setAttributeAll(Object attObject)
        int setAttributeAll(String attName, Object attObject)
        int setAttributeAll(Object attObject, String... path)
        int setAttributeAll(String attName, Object attObject, String... path)
        int addAttributeAll(String attName, Object attObject, String... path)
        void addAttribute(String attName, Object attObject)
        int countAttribute(String attName)
        Object removeAttribute(String attName)
        int removeAttributeAll()
        int removeAttributeAll(String attName)
        int removeAttributeAll(String attName, String... path)
        Object getAttribute()
        Object getAttribute(String attName)
        Object getAttribute(String attName, String[] name1, String... name2)
        Object getAttribute(String attName, String... path)

### Mapping and Listing  `RegexNode` occurences

        HashMap<String,ArrayList<RegexNode>> makeMap()
        HashMap<String,ArrayList<RegexNode>> makeMap(String[] name1, String... name2)
        HashMap<String,ArrayList<RegexNode>> makeMap(String... path)
        HashMap<String,ArrayList<RegexNode>> makeMap(HashMap<String,ArrayList<RegexNode>> oldMap, String[] name1, String... name2)
        HashMap<String,ArrayList<RegexNode>> makeMap(HashMap<String,ArrayList<RegexNode>> oldMap, String... path)
        ArrayList<RegexNode> makeList()
        ArrayList<RegexNode> makeList(String[] name1, String... name2)
        ArrayList<RegexNode> makeList(String... path)
