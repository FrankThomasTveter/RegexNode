
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

//
// Initial text:'foo foo foo <comment with foo> foo foo <more comments with foo>'
//
// Tree with comments hidden:
// 0#Groot# "foo foo foo [§] foo foo [§]" Root [0] no groups       
//                       1 1         2 2
//   1#comment# "<comment with foo>" => 0#(12 13) <-99 1 2> no groups         
//                                
//   2#comment# "<more comments with foo>" => 0#(22 23) <1 2 -99> no groups         
//                                      
// #### Total number of nodes defined = 2.
//
// Found comment:<comment with foo>
// Found comment:<more comments with foo>
//
// Tree with comments swapped:
// 0#Groot# "foo foo foo [§] foo foo [§]" Root [0] no groups       
//                       2 2         1 1
//   2#comment# "<more comments with foo>" => 0#(12 13) <-99 2 1> no groups         
//                                      
//   1#comment# "<comment with foo>" => 0#(22 23) <2 1 -99> no groups         
//                                
// #### Total number of nodes defined = 3.
//
// Final text:'foo foo foo <more comments with foo> foo foo <comment with foo>'
//
