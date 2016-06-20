
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

//
// Tree:
// 0#Groot# "foo foo foo [¤] foo foo [¤]" Root [0] -1 groups       
//                       1 1         2 2
//   1#comment# "<comment with foo>" => 0#(12 13) <-99 1 2> -1 groups         
//                              
//   2#comment# "<more comments with foo>" => 0#(22 23) <1 2 -99> -1 groups         
//                                    
// #### Total number of nodes defined = 2.
//
// Final text:'foo foo foo <comment with foo> foo foo <more comments with foo>'
//
