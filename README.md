# RegexNode
Un-tangled REGEX pattern matching

Have you ever wanted to "hide" your comments from a regular expression pattern matching search, 
and be able to "unhide" your comments afterwards? This JAVA library takes you even further. 

The basic ideas are demonstrated in the examples and summarised below.

Source-code: RegexNode.java

Examples: Example1.java Example2.java Example3.java Example4.java Example5.java Example6.java Example7.java

# Summary

The purpose of the RegexNode class is to simplify complicated regex matching and 
processing. The idea is to allow the user to "hide" parts of the text from the regex 
matching, or just do regex matching on the "hidden" parts of the text, or "hidden" parts 
of the "hidden" text etc. The "hidden" parts of the text can be recovered later.

The text can be searched ({@link #seek}) using normal regex syntax. Matches may be
processed and the original text replaced ({@link #replace}) following the same syntax.

If a match, or group within a match, is "hidden" by for instance {@link #hide}, it 
may be replaced by a label which can be empty. The label is available for later matching.
The "hidden" text is assigned a name so that it can be "unhidden" later for instance
by {@link #unhide}. When text is "unhidden" it replaces the corresponding label again. 

If a match is replaced and "hidden", the existing nodes within the match are moved to
the new node. Nodes can in this way be organised in a tree structure. Nodes and their 
child-nodes can be processed seperately from the rest of the node tree. The node tree 
structure may be navigated by looping over nodes with a given name using {@link #getNode}.
The order in which the nodes are put into the tree follows the order they appear in the text.
Existing nodes must be deleted explicitly using {@link #rmnode} or recovered using for 
instance {@link #uhideAll}. Only "hiding" parts of an existing label causes an error. 

The "ignore"-feature can be used to make only certain nodes visible to the processing.
"Ignorering" is in other words a method to deactivate pattern matching for parts of the 
node tree, for instance if a more general pattern may match already nodeged patterns that 
should not be processed by {@link #hideAll}. Ignored nodes are not processed by 
{@link #hideAll}, {@link #replaceAll} and {@link #unhideAll}. The "ignore" status of 
a node can be retrieved by {@link #getIgnore}. Text is by default "ignored", while new 
nodes are be default "unignored". Text that has not been "hidden" yet containing
nodes that are ignored can be "hidden" using {@link #hideTheRest}.  Specific nodes can be 
"ignored" by {@link #ignore}. 

Anchors are non-ascii characters that are defined and used as labels to
make the labels unique. 

The node tree structure can be displayed using {@link #toString}.

Dr.Scient. Frank Thomas Tveter
