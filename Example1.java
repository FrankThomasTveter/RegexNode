
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

public class Example1 {
    public static void main(String[] args) {

	// RegexNode example by Frank Thomas Tveter 2016.
	// This example demonstrates use of "hideAll", "ignoreAll" and "replaceAll".
	
	// initialise
	RegexNode regex=new RegexNode("foo foo foo <comment with foo> foo foo <more comments with foo>");
	regex.setNodeName("Groot"); // set the name of the root-node
	System.out.format("Initial text:'%s'\n\n", regex.getText());

	// define non-ASCII label character (not originally in the text)
	RegexNode.define("<Comment>");

	// hide comments in "comment"-node with non-ASCII label "<Comment>" in the "Groot" node
	regex.hideAll( "comment", "<.*?>", "<Comment>","*"); // non-gready search
	regex.ignoreAll("comment"); // do not process "comment"-nodes later on
	System.out.format("Tree with comments hidden:%s\n", regex.toString());

	// change "foo" to "bar"
	regex.replaceAll("foo","bar","*"); // replace all "foo" by "bar" in all (not-ignored) nodes

	// replace "foo" by "tar" in the comments
	regex.unignoreAll(); // do not ignore any nodes any more
	regex.replaceAll("foo","tar","comment"); // replace "foo" by "tar" in all "comment"-nodes

	// retrieve the final text
	regex.unhideAll(); // un-hide all nodes
	System.out.format("Final text:'%s'\n", regex.getText());
    }
}

//
// Initial text:'foo foo foo <comment with foo> foo foo <more comments with foo>'
//
// Tree with comments hidden:
// 0#Groot# "foo foo foo [§] foo foo [§]" Root [0] no groups       
//                       1 1         2 2
//   1#comment# "<comment with foo>" => 0#(12 13) #i <-99 1 2> no groups         
//                                
//   2#comment# "<more comments with foo>" => 0#(22 23) #i <1 2 -99> no groups         
//                                      
// #### Total number of nodes defined = 2.
//
// Final text:'bar bar bar <comment with tar> bar bar <more comments with tar>'
//
