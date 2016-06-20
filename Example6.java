
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

//
// Tree with groups:
// 0#Groot# "[§] [§] [§] [¤] [§] [§] [¤]" Root [0] 2 groups "f"(10,11) "oo"(11,13)       
//           3 3 4 4 5 5 1 1 6 6 7 7 2 2
//   3#foo# "[X][ Z] " => 0#(0 1) <-99 3 4> 2 groups "f"(0,1) "oo"(1,2)     
//           8 813 13
//     8#group1# "f" => 3#(0 1) <-99 8 13> no groups        
//                
//     13#group2# "oo" => 3#(1 2) <8 13 -99> no groups         
//                  
//   4#foo# "[X][ Z] " => 0#(2 3) <3 4 5> 2 groups "f"(0,1) "oo"(1,2)     
//           9 914 14
//     9#group1# "f" => 4#(0 1) <-99 9 14> no groups        
//                
//     14#group2# "oo" => 4#(1 2) <9 14 -99> no groups         
//                  
//   5#foo# "[ X] [ Z] " => 0#(4 5) <4 5 1> 2 groups "f"(0,1) "oo"(1,2)     
//           10 1015 15
//     10#group1# "f" => 5#(0 1) <-99 10 15> no groups         
//                 
//     15#group2# "oo" => 5#(1 2) <10 15 -99> no groups         
//                  
//   1#comment# "<comment with foo>" => 0#(6 7) <5 1 6> -1 groups         
//                                
//   6#foo# "[ X] [ Z] " => 0#(8 9) <1 6 7> 2 groups "f"(0,1) "oo"(1,2)     
//           11 1116 16
//     11#group1# "f" => 6#(0 1) <-99 11 16> no groups         
//                 
//     16#group2# "oo" => 6#(1 2) <11 16 -99> no groups         
//                  
//   7#foo# "[ X] [ Z] " => 0#(10 11) <6 7 2> 2 groups "f"(0,1) "oo"(1,2)     
//           12 1217 17
//     12#group1# "f" => 7#(0 1) <-99 12 17> no groups         
//                 
//     17#group2# "oo" => 7#(1 2) <12 17 -99> no groups         
//                  
//   2#comment# "<more comments with foo>" => 0#(12 13) <7 2 -99> -1 groups         
//                                      
// #### Total number of nodes defined = 17.
//
