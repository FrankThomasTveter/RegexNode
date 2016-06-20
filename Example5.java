
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
// Tree with an unfolded comment:
// 0#Groot# "foo foo foo [§]<comment with foo>[§] foo foo [¤]" Root [0] -1 groups       
//                       1 1                  3 3         2 2
//   1#comment# "" => 0#(12 13) <-99 1 3> -1 groups End3#         
//              
//      @labelFolded = "¤"
//   3#comment_# "" => 0#(31 32) <1 3 2> -1 groups Start1#          
//               
//      @matches = "1"
//   2#comment# "<more comments with foo>" => 0#(41 42) <3 2 -99> -1 groups         
//                                      
// #### Total number of nodes defined = 3.
//
// Final text:'bar bar bar <comment with bar> bar bar <more comments with foo>'
//
