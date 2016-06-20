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

//
// Node tree: 
// 0#Groot# "[§][¤][§][¤]" Root [0] -1 groups       
//           3 31 14 42 2
//   3#rest# "foo foo foo " => 0#(0 1) <-99 3 1> -1 groups      
                       
//   1#comment# "<comment with foo>" => 0#(1 2) <3 1 4> -1 groups         
                                
//   4#rest# " foo foo " => 0#(2 3) <1 4 2> -1 groups      
                    
//   2#comment# "<more comments with foo>" => 0#(3 4) <4 2 -99> -1 groups         
                                      
// #### Total number of nodes defined = 4.

// Final text:'foo foo foo <comment with foo> foo foo <more comments with foo>'
//
