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

public class Example9 {
    public static void main(String[] args) {

	// RegexNode example by Frank Thomas Tveter 2016.
	// This example demonstrates how to hide comments and strings.
	
	RegexNode regex=new RegexNode("text 'string' ! prog's comments\nMore text 'str'# 'string'");

	System.out.format("Initial tree: %s\n", regex.toString());

        RegexNode.define("< >",        "[ \\t]*");
        RegexNode.define("<d>",        "[\\d]*");
        RegexNode.define("<#>",        "[^\\n]*");
        RegexNode.define("<#/>",       "[^\\n\\/]*");
        RegexNode.define("<String>");
        RegexNode.define("<Brackets>");

	regex.hideAll( "string",       "([\\\"\\\'])[^\\\"\\\'\\n]*\\1",    "<String>", "*"); // hide strings
        regex.hideAll( "comment",      "(?m)^[cC]<#>\n",                    "\n", "*");  // hide line comments
        regex.hideAll( "comment",      "(?m)^< >!.*\n",                     "\n", "*");  // hide comments at end of line
        regex.hideAll( "comment",      "(?m)!.*\n",                         "\n", "*");  // hide comments at end of line
        //regex.dumpToFile("analysis1.tree");
        regex.hideAll( "continuation", "(?m)\\n[ ]{5}[^\\d ]",             " ", "*");  // hide comments at end of line
        regex.hideAll( "continuation", "(?m)\\&[ \t]*\\n[ \t]*\\&",         " ", "*");  // hide comments at end of line
        regex.hideAll( "semicolon",    ";",                                 "\n","*"); 

        regex.ignoreAll("string");
        regex.ignoreAll("comment");

        while(regex.hideAny( "_Brackets", "(?m)(?i)\\(([^\\(\\)<Content>]*)\\)", "<Brackets>","*")) {
            regex.hideNodeGroup("content", "<Content>", 1, "*","_Brackets");
            regex.setNodeNameAll("Brackets","*","_Brackets");
        };

	System.out.format("Node tree: %s\n", regex.toString());
    }
}
// Initial tree: 
// 0## "text 'string' ! prog's comments
//      More text 'str'# 'string'" Root [0] -1 groups  
                              
// #### Total number of nodes defined = 0.

// Node tree: 
// 0## "text [§] [
//           1 1 4
//      ]More text [§]# [§]" Root [0] -1 groups  
//      4          2 2  3 3
//   1#string# "'string'" => 0#(5 6) #i <-99 1 4> 1 groups "'"(0,1)        
                     
//   4#comment# "! prog's comments
//        " => 0#(7 8) #i <1 4 2> no groups         
       
//   2#string# "'str'" => 0#(18 19) #i <4 2 3> 1 groups "'"(0,1)        
                  
//   3#string# "'string'" => 0#(21 22) #i <2 3 -99> 1 groups "'"(0,1)        
                     
// #### Total number of nodes defined = 4.
