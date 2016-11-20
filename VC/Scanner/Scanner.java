/**
 **	Scanner.java                        
 **/

package VC.Scanner;

import java.io.CharArrayWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import VC.ErrorReporter;

public final class Scanner {

    private SourceFile sourceFile;
    private boolean debug;

    private ErrorReporter errorReporter;
    private StringBuffer currentSpelling;
    private char currentChar;
    private SourcePosition sourcePos;
    
    private int line;
    private int charStart;
    private int charFinish;
    
    private static final Character[] escape = new Character[] {'b','f','n','r','t','\'','"','\\'  };
    private static final Set<Character> escapeChars = new HashSet<Character>(Arrays.asList(escape));

    // =========================================================

    public Scanner(SourceFile source, ErrorReporter reporter) {
        sourceFile = source;
        errorReporter = reporter;
        currentChar = sourceFile.getNextChar();
        debug = false;

        // you may initialise your counters for line and column numbers here
        line = 1; charStart = 1; charFinish = 1;
    }

    public void enableDebugging() {
        debug = true;
    }

    // accept gets the next character from the source program.
    private void accept() {
        //1. save the lexeme of the current token incrementally here
        //2: increment line and column counters here
        if( currentChar != '\n' ){
            if( currentChar != '"' ) currentSpelling.append( currentChar ); //only ignore '"' just for spelling print
            charFinish ++;
        }
        else {line ++; charStart = 1; charFinish = 1;}
        
        currentChar = sourceFile.getNextChar();
        // you may save the lexeme of the current token incrementally here
        // you may also increment your line and column counters here
    }

    // inspectChar returns the n-th character after currentChar
    // in the input stream.
    //
    // If there are fewer than nthChar characters between currentChar
    // and the end of file marker, SourceFile.eof is returned.
    //
    // Both currentChar and the current position in the input stream
    // are *not* changed. Therefore, a subsequent call to accept()
    // will always return the next char after currentChar.

    private char inspectChar(int nthChar) {
        return sourceFile.inspectChar(nthChar);
    }

    private int nextToken() {
        // Tokens: separators, operators, literals, identifiers and keywords
        switch (currentChar) {
            // separators below:
            case '(':
                accept();
                return Token.LPAREN;
            case ')':
                accept();
                return Token.RPAREN;
            case '{':
                accept();
                return Token.LCURLY;
            case '}':
                accept();
                return Token.RCURLY;
            case '[':
                accept();
                return Token.LBRACKET;
            case ']':
                accept();
                return Token.RBRACKET;
            case ';':
                accept();
                return Token.SEMICOLON;
            case ',':
                accept();
                return Token.COMMA;
                
            // operators below:
            case '+':
                accept();
                return Token.PLUS;
            case '-':
                accept();
                return Token.MINUS;
            case '*':
                accept();
                return Token.MULT;
            case '/':
                accept();
                return Token.DIV;
            case '<':   // '<' or '<='
                accept();
                if (currentChar == '=') {
                    accept();
                    return Token.LTEQ;
                } else {
                    return Token.LT;
                }
            case '>':   // '>' or '>='
                accept();
                if (currentChar == '=') {
                    accept();
                    return Token.GTEQ;
                } else {
                    return Token.GT;
                }
            case '=':  // '=' or '=='
                accept();
                if (currentChar == '=') {
                    accept();
                    return Token.EQEQ;
                } else {
                    return Token.EQ;
                }
                
            case '!':  // '!' or '!='
                accept();
                if (currentChar == '=') {
                    accept();
                    return Token.NOTEQ;
                } else {
                    return Token.NOT;
                }
            case '|':  // '||'
                accept();
                if (currentChar == '|') {
                    accept();
                    return Token.OROR;
                } else {
                    return Token.ERROR;
                }
            case '&':  // '||'
                accept();
                if (currentChar == '&') {
                    accept();
                    return Token.ANDAND;
                } else {
                    return Token.ERROR;
                }
                
            // literals below:
            // int/float:
            case '.': case '0':case '1':case '2':case '3':case '4':case '5':case '6':case '7':case '8':case '9':
                int result = intFloatWrapChecker();
                if( result == 1 ) return Token.INTLITERAL;
                else if( result == -1 ) return Token.FLOATLITERAL;
                else return Token.ERROR;

            //boolean:
            case 't':  //true
                char[] chArray3 = longestMatch();
                String str1 = String.valueOf(chArray3);
                if( str1.equals("true") ){
                    accept();accept();accept();accept();
                    return Token.BOOLEANLITERAL;
                }else break;
            case 'f':  //false
                char[] chArray4 = longestMatch();
                String str2 = String.valueOf(chArray4);
                if( str2.equals("false") ){
                    accept();accept();accept();accept();accept();
                    return Token.BOOLEANLITERAL;
                }else break;
                
            //string:
            case '"':
                accept();
                if( stringChecker() == true ) return Token.STRINGLITERAL;  //valid
                else return -1; 
                
            //Identifier/Keyword(be put under switch, since too many cases...)
                
// need to be tested !!!!!
//            case '\n':
//                currentChar = sourceFile.getNextChar();  //get the beginning char of the next line
//                charStart = 1;charFinish = 1;  line ++;  //must update line/column counter
//                return -1; //which should be processed after this
                
            case SourceFile.eof:
                currentSpelling.append(Token.spell(Token.EOF)); //append the para text to the original text
                return Token.EOF;
            default:
                break;
        }
        
        // Identifier/Keyword: letter(a-zA-Z)
        if( isLetter(currentChar) ){
            accept();
            iterateLettersAndDigits();
            return Token.ID;
        }

        accept();
        return Token.ERROR;
    }
    
    public int intFloatWrapChecker(){
        char[] chArray = longestMatch();
        for(int i=chArray.length-1; i>-1; i--){
            char[] subArray = Arrays.copyOfRange(chArray, 0, i+1);
            if( intAndFloatChecker(subArray) == 1 ){  //int
                iteration(i+1); 
                return 1;   // int top indicator
            }else if( intAndFloatChecker(subArray) == -1 ){ //float
                iteration(i+1);
                return -1;  // float top indicator
            }else continue;
        } //anyway it will satisfy one of the above cases within the for loop for number prefix
        //below will only possible for dot prefix
        accept();
        return 0; // neither int nor float top indicator 
    }

    public void iteration( int count ){
        while( count != 0 ){
            accept(); count --;
        }
    }
    
    public char[] longestMatch(){
        int length = 0;
        char nextChar = currentChar;
        CharArrayWriter chw = new CharArrayWriter(); //use CharArrayWriter class
        while( nextChar != ' ' && nextChar != '\n' && nextChar != ';' && nextChar != ')'){
            chw.append(nextChar);
            nextChar = inspectChar(++length);
        }
        return chw.toCharArray(); 
    }
    
    public boolean digitsChecker(char[] chArray){
        for(char ch : chArray ){
            if( !Character.isDigit(ch) ) return false;
        }
        return true;
    }
    
    public boolean identifyFloatWithPrefixExpo(char[] chArray){
        int len = chArray.length;
        if( chArray[0] == '+' || chArray[0] == '-' ){
            if( len == 1 ) return false;  //not float
            return digitsChecker( Arrays.copyOfRange(chArray, 1, len) );
        }else return digitsChecker( Arrays.copyOfRange(chArray, 0, len) );
    }
    public boolean identifyFloatWithPrefixDot( char[] chArray ){
        int len = chArray.length;
        if( len == 0 ) return false; //not float
        if( chArray[0] == 'e' || chArray[0] == 'E' ){
            if( len == 1 ) return false;  //neither int nor float
            else{
                if( identifyFloatWithPrefixExpo( Arrays.copyOfRange(chArray, 1, len)) ) return true; //float
                else return false; //neither int nor float
            }
        }else{
            if( !Character.isDigit(chArray[0]) ) return false;
            int i = 0;
            while( Character.isDigit(chArray[i]) ){  //2e
                if( i >= (len-1) ) return true;//float
                i++;
            }
            if( chArray[i] == 'e' || chArray[i] == 'E' ){
                if( i >= (len-1) ) return false;  //neither int nor float
                else{
                    if( identifyFloatWithPrefixExpo( Arrays.copyOfRange(chArray, i+1, len)) ) return true; //float
                    else return false; //neither int nor float
                }
            } else return false;
        }
    }   
    public int intAndFloatChecker(char[] chArray){
        int len = chArray.length;
        if( chArray[0] == '.' ){ //dot (go to 1st branch)
            if( len == 1 || !Character.isDigit(chArray[1]) ) return 0; //neither int nor float
            else{
                int i = 1;
                while( Character.isDigit(chArray[i]) ){
                    if( i >= (len-1) ) return -1; //float
                    i++;
                }
                if( chArray[i] == 'e' || chArray[i] == 'E' ){
                    if( i >= (len-1) ) return 0;  //neither int nor float
                    else{
                        if( identifyFloatWithPrefixExpo( Arrays.copyOfRange(chArray, i+1, len)) ) return -1; //float
                        else return 0; //neither int nor float
                    }
                }else return 0; //neither int nor float
            }
        }else{ //digit (go to 1st/2nd/3rd branch)
            int i = 0;
            while( Character.isDigit(chArray[i]) ){
                if( i >= (len-1) ) return 1; //int indicator
                i ++;
            }
            //otherwise it is not int at least
            if( chArray[i] == '.' ){ //digit+.
                if( i >= (len-1) ) return -1; //float indicator: digit+.
                else{
                    if( identifyFloatWithPrefixDot( Arrays.copyOfRange(chArray, i+1, len)) ) return -1; //float
                    else return 0; //neither int nor float
                }
            }else if( chArray[i] == 'e' || chArray[i] == 'E' ){
                if( i >= (len-1) ) return 0;  //neither int nor float
                else{
                    if( identifyFloatWithPrefixExpo( Arrays.copyOfRange(chArray, i+1, len)) ) return -1; //float
                    else return 0; //neither int nor float
                }
            }
            else return 0; //neither int nor float
        }
    }
    
    boolean isLetter(char ch){  //a-zA-Z_
        if( (currentChar>=65 && currentChar<=90) || (currentChar>=97 && currentChar<=122) || currentChar == 95 ){
            return true;
        }else return false;
    }
    
    void iterateLettersAndDigits(){
        while( Character.isDigit(currentChar) || isLetter(currentChar) ){
            accept();
        }
    }
    
    public boolean stringChecker(){
        int beginIndex=charStart;
        char tmpNextChar;
        boolean invalidEscape = false;
        while( currentChar != '\n' && currentChar != '"' ){
            if( currentChar == '\\' ){
                tmpNextChar = inspectChar(1);
                if( !escapeChars.contains(tmpNextChar) ){   //invalid escape character
                    invalidEscape = true;
                    errorReporter.reportError( "%: illegal escape character", "\\"+String.valueOf(tmpNextChar), new SourcePosition( line, beginIndex, charFinish ) );
                    accept();
                }else{
                    switch (tmpNextChar) {    
                        case 'b': currentSpelling.append('\b');break;
                        case 'f': currentSpelling.append('\f');break;
                        case 'n': currentSpelling.append('\n');break;
                        case 'r': currentSpelling.append('\r');break;
                        case 't': currentSpelling.append('\t');break;
                        case '\'': currentSpelling.append('\'');break;
                        case '"': currentSpelling.append('\"');break;
                        case '\\': currentSpelling.append('\\');break;
                    }
                    charFinish += 2;  // \t
                    currentChar = sourceFile.getNextChar(); currentChar = sourceFile.getNextChar(); // \t
                }
            }else accept();
        }
        if( currentChar == '\n'  ){
            errorReporter.reportError( "%: unterminated string", currentSpelling.toString(), new SourcePosition( line, beginIndex, beginIndex ) );
            System.out.println( new Token( Token.STRINGLITERAL, currentSpelling.toString(), new SourcePosition( line, beginIndex, charFinish-1 ) ) );
            return false;  // '\n', which means it is unterminated
        }else if( currentChar == '"' && invalidEscape == true){
            accept();
            System.out.println( new Token( Token.STRINGLITERAL, currentSpelling.toString(), new SourcePosition( line, beginIndex, charFinish-1 ) ) );
            return false;  // '\n', which means it is unterminated
        }
        else{
            accept(); return true; // '"', which is valid
        }
    }
    
    void skipSpaceAndCommentAndLineTerminator() {
        skipLineTerminator();
        //the goal is to get following valid/invalid currentChar which are not space or comments.
        while( currentChar == ' ' || currentChar == '/' || currentChar == '\t'){
            if( currentChar == '\t' ){
                int offset = 0;
                if( charStart % 8 != 0 ) offset = 8 - charStart % 8;
                else offset = 0;
                while( offset >= 0 ){
                    charStart ++; charFinish ++; offset--;
                }
                currentChar = sourceFile.getNextChar();
                skipLineTerminator();
            } else if( currentChar == ' ' ){
                currentChar = sourceFile.getNextChar();
                charStart ++; charFinish ++;
                skipLineTerminator();
            }
            else{
                char followChar = inspectChar(1);  //the following char
                if( followChar == '/' ){
                    //then the currentChar should skip to the beginning of the next line
                    //in this case, no need to check EOF, since ass specification place EOF always at the beginning of last line as terminate, so it must has '\n' before EOF.
                    while( followChar != '\n' ){
                        followChar = sourceFile.getNextChar();
                    }
                    currentChar = sourceFile.getNextChar(); //the beginning char at the next line /*result*/
                    charStart = 1; charFinish = 1; line ++; //update line and column counter here
                    skipLineTerminator();
                    //Cannot just break in this case, since at the new line, it is still possible to come across whitespace and another comments
                }
                else if(followChar == '*'){  //  /*\nEOF
                    int offset = 2;             
                    followChar = inspectChar(offset);
                    while( !(followChar == '*' && inspectChar(offset+1) == '/') ){
                        followChar = inspectChar( ++offset );
                        if( followChar == SourceFile.eof ){
                            errorReporter.reportError( ": unterminated comment", "", new SourcePosition( line, charStart, charFinish ) );
                            while( offset != 0 ){
                                followChar = sourceFile.getNextChar(); 
                                if( followChar == '\n' ) {charStart = 0; charFinish = 0; line ++;}
                                else {charStart ++; charFinish ++;}
                                offset--;
                            }
                            System.out.println( new Token( Token.EOF, "$", new SourcePosition( line, charStart, charFinish ) ) );
                            System.exit(0);
                        }
                    }
                    // has terminated comment '*/' and already find it. Now just get the char after '*/'
                    while(true){
                        if( followChar == '\n' ) {charStart = 0; charFinish = 0; line ++;}
                        else {charStart ++; charFinish ++;}
                        followChar = sourceFile.getNextChar(); 
                        offset--;
                        if( offset == -2 ) break; 
                    }
                    currentChar = followChar; /*result*/
                    skipLineTerminator();
                    //Cannot just break in this case, after '/* */' comment, it is still possible to come across whitespace and another comments
                }
                else break;
            }
        }
    }
    void skipLineTerminator(){
        while( currentChar == '\n' ){
            currentChar = sourceFile.getNextChar();
            line ++; charStart = 1; charFinish = 1;
        }
    }
   
    public Token getToken() {
        Token tok;
        int kind;
 
        charStart = charFinish;  //update the charStart for every getToken 
        
        // skip white space and comments and current line terminator if it is 
        skipSpaceAndCommentAndLineTerminator();
        
        // initialize current spell object
        currentSpelling = new StringBuffer("");
        
        // much work to do here:
        // 1. return the kind of current token
        // 2. build the spelling of the current token
        // 3. build the position of the current token
        // 4. update the cursor(line/column counter) to be latest
        kind = nextToken(); 

        // create source position object to be part of token object created below
        //since it was reset within 'nextToken' method to 1/1 already
        if( currentChar == SourceFile.eof ) sourcePos = new SourcePosition( line, charStart, charFinish );
        else sourcePos = new SourcePosition( line, charStart, charFinish-1 );
        
        // real create token object with its kind, spelling and position
        tok = new Token(kind, currentSpelling.toString(), sourcePos);
        
        // * do not remove these three lines
        if ( debug && (kind != -1)) System.out.println(tok);
        return tok;
    }
}
