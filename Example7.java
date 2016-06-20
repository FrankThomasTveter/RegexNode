import java.util.ArrayList;
import java.util.HashMap;

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

//
// Tree:
// 0#Groot# "foo foo foo [¤] foo foo [¤]" Root [0] -1 groups       
//                       1 1         2 2
//   1#comment# "<[§] [§] [§]>" => 0#(12 13) <-99 1 2> no groups         
//                3 3 4 4 5 5 
//     3#word# "comment" => 1#(1 2) <-99 3 4> no groups      
//                    
//     4#word# "with" => 1#(3 4) <3 4 5> no groups      
//                 
//     5#word# "foo" => 1#(5 6) <4 5 -99> no groups      
//                
//   2#comment# "<[§] [§] [§] [§]>" => 0#(22 23) <1 2 -99> no groups         
//                6 6 7 7 8 8 9 9 
//     6#word# "more" => 2#(1 2) <-99 6 7> no groups      
//                 
//     7#word# "comments" => 2#(3 4) <6 7 8> no groups      
//                     
//     8#word# "with" => 2#(5 6) <7 8 9> no groups      
//                 
//     9#word# "foo" => 2#(7 8) <8 9 -99> no groups      
//                
// #### Total number of nodes defined = 9.
//
// Found 2 occurences of 'foo' in the comments.
//
// Final text:'foo foo foo <comment with foo> foo foo <more comments with foo>'
//
