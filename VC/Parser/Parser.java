/*
 * Parser.java            
 *
 * This parser for a subset of the VC language is intended to 
 *  demonstrate how to create the AST nodes, including (among others): 
 *  [1] a list (of statements)
 *  [2] a function
 *  [3] a statement (which is an expression statement), 
 *  [4] a unary expression
 *  [5] a binary expression
 *  [6] terminals (identifiers, integer literals and operators)
 *
 * In addition, it also demonstrates how to use the two methods start 
 * and finish to determine the position information for the start and 
 * end of a construct (known as a phrase) corresponding an AST node.
 *
 * NOTE THAT THE POSITION INFORMATION WILL NOT BE MARKED. HOWEVER, IT CAN BE
 * USEFUL TO DEBUG YOUR IMPLEMENTATION.
 *
 * (09-|-April-|-2016)


program       -> func-decl
func-decl     -> type identifier "(" ")" compound-stmt
type          -> void
identifier    -> ID
// statements
compound-stmt -> "{" stmt* "}" 
stmt          -> expr-stmt
expr-stmt     -> expr? ";"
// expressions 
expr                -> additive-expr
additive-expr       -> multiplicative-expr
                    |  additive-expr "+" multiplicative-expr
                    |  additive-expr "-" multiplicative-expr
multiplicative-expr -> unary-expr
	            |  multiplicative-expr "*" unary-expr
	            |  multiplicative-expr "/" unary-expr
unary-expr          -> "-" unary-expr
		    |  primary-expr

primary-expr        -> identifier
 		    |  INTLITERAL
		    | "(" expr ")"
 */

package VC.Parser;

import VC.Scanner.Scanner;
import VC.Scanner.SourcePosition;
import VC.Scanner.Token;
import VC.ErrorReporter;
import VC.ASTs.*;

public class Parser {
    private Scanner scanner;
    private ErrorReporter errorReporter;
    private Token currentToken;
    private SourcePosition previousTokenPosition;
    private SourcePosition dummyPos = new SourcePosition();

    public Parser(Scanner lexer, ErrorReporter reporter) {
        scanner = lexer;
        errorReporter = reporter;
        previousTokenPosition = new SourcePosition();
        currentToken = scanner.getToken(); // get the first token of the input program !
    }

    // match checks to see f the current token matches tokenExpected.
    // If so, fetches the next token.
    // If not, reports a syntactic error.

    void match(int tokenExpected) throws SyntaxError {
        if (currentToken.kind == tokenExpected) {
            previousTokenPosition = currentToken.position;
            currentToken = scanner.getToken();
        } else {
            syntacticError("\"%\" expected here", Token.spell(tokenExpected));
        }
    }

    void accept() {
        previousTokenPosition = currentToken.position;
        currentToken = scanner.getToken();
    }

    void syntacticError(String messageTemplate, String tokenQuoted)
            throws SyntaxError {
        SourcePosition pos = currentToken.position;
        errorReporter.reportError(messageTemplate, tokenQuoted, pos);
        throw (new SyntaxError());
    }

    // start records the position of the start of a phrase.
    // This is defined to be the position of the first
    // character of the first token of the phrase.

    void start(SourcePosition position) {
        position.lineStart = currentToken.position.lineStart;
        position.charStart = currentToken.position.charStart;
    }

    // finish records the position of the end of a phrase.
    // This is defined to be the position of the last
    // character of the last token of the phrase.

    void finish(SourcePosition position) {
        position.lineFinish = previousTokenPosition.lineFinish;
        position.charFinish = previousTokenPosition.charFinish;
    }

    void copyStart(SourcePosition from, SourcePosition to) {
        to.lineStart = from.lineStart;
        to.charStart = from.charStart;
    }

    // ========================== PROGRAMS ========================copyStart

    public Program parseProgram() {
        Program programAST = null;
        SourcePosition programPos = new SourcePosition();
        start(programPos);
        try {
            // empty program
            if( currentToken.kind == Token.EOF ){           
                programAST = new Program(new EmptyDeclList(dummyPos), dummyPos);
                return programAST;
            }
            
            List dlAST = parseFuncOrVarDeclList( true, true );
            finish(programPos);
            programAST = new Program(dlAST, programPos); 
            if (currentToken.kind != Token.EOF) {
              syntacticError("\"%\" unknown type", currentToken.spelling);
            }
        } catch (SyntaxError s) {
            return null;
        }
        return programAST;

    }

    // ========================== DECLARATIONS ========================
    List parseFuncOrVarDeclList( boolean funcAllowFlag, boolean globalVarFlag ) throws SyntaxError {
        if( currentToken.kind == Token.EOF ){
            return new EmptyDeclList(dummyPos);
        }
        List list = null;
        Decl decl = null;
        
        SourcePosition listPos = new SourcePosition();
        start(listPos);
        SourcePosition declPos = new SourcePosition();
        start(declPos);
        
        Type tAST = parseType( null );
        Ident idAST = parseIdent();
        if( funcAllowFlag == false && currentToken.kind == Token.LPAREN ){
            syntacticError("function definition here is not allowed", "");
        }
        
        if (currentToken.kind == Token.LPAREN) { // function declaration
            List paraList = parseParaList();
            Stmt compoundStmt = parseCompoundStmt();
            finish(declPos);
            decl = new FuncDecl(tAST, idAST, paraList, compoundStmt, declPos);
            
            list = parseFuncOrVarDeclList( true, true );
            finish(listPos);
            
            list = new DeclList(decl, list, listPos);
        } 
        else {                                 // variable declaration
            list = parseInitDeclaratorListBesidesFirstIdent(tAST, idAST, true, globalVarFlag);
            //match(Token.SEMICOLON);
        }
        
        return list;
    }

    //Not recursive currently
    DeclList parseInitDeclaratorListBesidesFirstIdent(Type tAST, Ident idAST, boolean funcAllowFlag, boolean globalVarFlag) throws SyntaxError {
        DeclList declList = null;
        SourcePosition position = new SourcePosition();
        copyStart(tAST.position, position);
        
        Decl firstItemDecl = parseInitDeclaratorBesidesFirstIdent(tAST, idAST, globalVarFlag);
        
        // whole recursion within parseInitDeclarator.
        List list = parseInitDeclarator( tAST, funcAllowFlag, globalVarFlag ); 
        finish(position);
        declList = new DeclList(firstItemDecl, list, position);
        
        return declList;
    }
    
    // Type is used to construct arrayType if it is, while Ident is used to keep the start position of the identifier 
    Type parseArrayTypeIfItIs( Type tAST, Ident idAST )  throws SyntaxError {
        Type tASTReal = parseType( tAST );
        
        Type arrayType = null;
        if (currentToken.kind == Token.LBRACKET) {
            SourcePosition arrayPos = new SourcePosition();
            copyStart(idAST.position, arrayPos);
            accept();
            if (currentToken.kind == Token.INTLITERAL){
                SourcePosition intExprPosition = new SourcePosition();
                start(intExprPosition);
                IntLiteral intLiteral = parseIntLiteral();
                finish(intExprPosition);
                
                IntExpr intExpr = new IntExpr(intLiteral, intExprPosition);
                
                match(Token.RBRACKET);
                finish(arrayPos);
                arrayType = new ArrayType(tASTReal, intExpr, arrayPos);
            }
            else{
                match(Token.RBRACKET);
                finish(arrayPos);
                arrayType = new ArrayType(tASTReal, new EmptyExpr(dummyPos), arrayPos);
            }
        }
        return arrayType;
    }
    
    // No recursion here since it is the first declaration exceptionally 
    Decl parseInitDeclaratorBesidesFirstIdent(Type tAST, Ident idAST, boolean globalVar) throws SyntaxError {
        Decl decl = null;
        SourcePosition position = new SourcePosition();
        copyStart(idAST.position, position);
        
        Type arrayType = parseArrayTypeIfItIs(tAST, idAST);
        if( arrayType != null ) tAST = arrayType;
        Expr expr = null;
        if (currentToken.kind == Token.EQ) {
            Operator opAST = acceptOperator();
            expr = parseInitialiser();
            finish(position);
            if( globalVar == true ) decl = new GlobalVarDecl(tAST, idAST, expr, position);
            else decl = new LocalVarDecl(tAST, idAST, expr, position);
        }
        else{
            expr = new EmptyExpr(dummyPos);
            finish(position);
            if( globalVar == true ) decl = new GlobalVarDecl(tAST, idAST, expr, position);
            else decl = new LocalVarDecl(tAST, idAST, expr, position);
        }
        
        return decl;
    }
    
    Decl createVarDecl(Type tAST, Type arrayType, Ident ident, boolean globalVarFlag, SourcePosition position) throws SyntaxError{
        Type tASTReal = parseType( tAST );
        Expr expr = null;
        Decl decl = null;
        if( currentToken.kind == Token.EQ ){
            Operator opAST = acceptOperator();
            expr = parseInitialiser();
            finish(position);
            if( arrayType != null ){
                if( globalVarFlag == true ) decl = new GlobalVarDecl(arrayType, ident, expr, position);
                else decl = new LocalVarDecl(arrayType, ident, expr, position);
            }else{
                if( globalVarFlag == true ) decl = new GlobalVarDecl(tASTReal, ident, expr, position);
                else decl = new LocalVarDecl(tASTReal, ident, expr, position);
            }
        }
        else{
            finish(position);
            if( arrayType != null ){
                if( globalVarFlag == true ) decl = new GlobalVarDecl(arrayType, ident, new EmptyExpr(dummyPos), position);
                else decl = new GlobalVarDecl(arrayType, ident, new EmptyExpr(dummyPos), position);
            }else{
                if( globalVarFlag == true ) decl = new GlobalVarDecl(tASTReal, ident, new EmptyExpr(dummyPos), position);
                else decl = new LocalVarDecl(tASTReal, ident, new EmptyExpr(dummyPos), position);
            }
        }
        
        return decl;
    }
    
    List parseInitDeclarator(Type tAST, boolean funcAllowFlag, boolean globalVarFlag ) throws SyntaxError {
        List list = null;
        if (currentToken.kind == Token.COMMA) { // at least two items if legal. // eg: int i, j;
            accept();
            SourcePosition listPos = new SourcePosition();
            start(listPos);
            
            SourcePosition DecPos = new SourcePosition();
            start(DecPos);

            Ident ident = parseDeclarator();
            Type arrayType = null;
            Type arrayTypeTemp = parseArrayTypeIfItIs(tAST, ident);
            if (arrayTypeTemp != null) arrayType = arrayTypeTemp;

            finish(DecPos);
            Decl decl = createVarDecl(tAST, arrayType, ident, globalVarFlag, DecPos);

            List list1 = parseInitDeclarator(tAST, funcAllowFlag, globalVarFlag); // recursion within
            finish(listPos);
            list = new DeclList(decl, list1, listPos);
        }
        // only one item if legal. This case, it should call upper parseFuncOrVarDeclList, since it should begin from Type Ident,...
        else {
            match(Token.SEMICOLON); // skip semicolon here within the whole recursion.
            if (currentToken.kind == Token.VOID || currentToken.kind == Token.BOOLEAN || currentToken.kind == Token.INT || currentToken.kind == Token.FLOAT) {
                list = (DeclList) parseFuncOrVarDeclList(funcAllowFlag, globalVarFlag);
            } else {                // should add an empty declList anyway
                list = new EmptyDeclList(dummyPos);
            }
        }

        return list;
    }
    
    // identifier ("[" INTLITERAL? "]")?
    Ident parseDeclarator() throws SyntaxError {
        Ident ident = parseIdent();
        return ident;
    }
   
    //initialiser -> expr 
    //             | "{" expr ( "," expr )* "}"
    Expr parseInitialiser() throws SyntaxError {
        Expr expr = null;
        SourcePosition position = new SourcePosition();
        start(position);
        
        if( currentToken.kind == Token.LCURLY ){
            accept();
            List list = parseExprList();
            match(Token.RCURLY);
            finish(position);
            expr = new InitExpr(list, position);
        }
        else {
            expr = parseExpr();
        }
        
        return expr;
    }
    
    List parseExprList() throws SyntaxError {
        List exprList = null;
        SourcePosition position = new SourcePosition();
        start(position);
        
        Expr expr = parseExpr();
        if( currentToken.kind == Token.COMMA ){
            accept(); 
            List exprList1 = parseExprList();
            finish(position);
            exprList = new ExprList(expr, exprList1, position);
        }
        else{
            finish(position);
            exprList = new ExprList(expr, new EmptyExprList(dummyPos), position);
        }
        return exprList;
    }

    // ======================== TYPES ==========================
    Type parseType( Type inheriteType ) throws SyntaxError {
        Type typeAST = null;
        if( inheriteType == null ){
            SourcePosition typePos = new SourcePosition();
            start(typePos);
            switch (currentToken.kind) {
                case Token.VOID:
                    accept(); finish(typePos); typeAST = new VoidType(typePos); break;
                case Token.BOOLEAN:
                    accept(); finish(typePos); typeAST = new BooleanType(typePos); break;
                case Token.INT:
                    accept(); finish(typePos); typeAST = new IntType(typePos); break;
                case Token.FLOAT:
                    accept(); finish(typePos); typeAST = new FloatType(typePos); break;
                default:
                    syntacticError("\"%\" wrong result type for a function", currentToken.spelling); break;
            }
        }else{
            SourcePosition typePos = new SourcePosition();
            copyStart(inheriteType.position, typePos);
            typePos.charStart = inheriteType.position.charStart;
            typePos.charFinish = inheriteType.position.charFinish;
            if( inheriteType instanceof VoidType ) typeAST = new VoidType(typePos);
            else if( inheriteType instanceof BooleanType ) typeAST = new BooleanType(typePos);
            else if( inheriteType instanceof IntType ) typeAST = new IntType(typePos);
            else if( inheriteType instanceof FloatType ) typeAST = new FloatType(typePos);
            else syntacticError("\"%\" wrong result type for a function", currentToken.spelling);
        }
        return typeAST;
    }

    // ======================= STATEMENTS ==============================
    Stmt parseCompoundStmt() throws SyntaxError {
        Stmt cAST = null;
        SourcePosition stmtPos = new SourcePosition();
        start(stmtPos);
        
        match(Token.LCURLY);
        
        List varDecList = null;
        if (currentToken.kind == Token.VOID || currentToken.kind == Token.BOOLEAN || currentToken.kind == Token.INT || currentToken.kind == Token.FLOAT) {
            varDecList= parseVarDeclList();
        }
        else varDecList = new EmptyDeclList(dummyPos);
        
        List stmtList = null;
        if( currentToken.kind == Token.RCURLY ) stmtList = new EmptyStmtList(dummyPos); 
        else stmtList = parseStmtList();  //emptyStmt is also possible
        
        match(Token.RCURLY);
        finish(stmtPos);
       
        if( varDecList instanceof EmptyDeclList && stmtList instanceof EmptyStmtList ){
            cAST = new EmptyCompStmt(dummyPos);
        }
        else cAST = new CompoundStmt(varDecList, stmtList, stmtPos);
        return cAST;
    }
    
    List parseVarDeclList() throws SyntaxError {
        DeclList declList = null;
        Type tAST = parseType( null );
        Ident idAST = parseIdent();
        declList = parseInitDeclaratorListBesidesFirstIdent(tAST, idAST, false, false);
        //match(Token.SEMICOLON); has already skip it within upper function
        return declList;
    }
    
    List parseStmtList() throws SyntaxError {
        List slAST = null;
        SourcePosition stmtPos = new SourcePosition();
        start(stmtPos);

        if (currentToken.kind != Token.RCURLY) {
            Stmt sAST = parseStmt();
            {
                if (currentToken.kind != Token.RCURLY) {
                    slAST = parseStmtList();
                    finish(stmtPos);
                    slAST = new StmtList(sAST, slAST, stmtPos);
                } else {
                    finish(stmtPos);
                    slAST = new StmtList(sAST, new EmptyStmtList(dummyPos), stmtPos);
                }
            }
        } else
            slAST = new EmptyStmtList(dummyPos);

        return slAST;
    }
    
    Stmt parseStmt() throws SyntaxError {
        Stmt sAST = null;
        switch (currentToken.kind) {
            case Token.LCURLY:
                sAST = parseCompoundStmt();
                break;
            case Token.IF:
                sAST = parseIfStmt();
                break;
            case Token.FOR:
                sAST = parseForStmt();
                break;
            case Token.WHILE: 
                sAST = parseWhileStmt(); 
                break;
            case Token.BREAK:
                sAST = parseBreakStmt();
                break;
            case Token.CONTINUE:
                sAST = parseContinueStmt();
                break;
            case Token.RETURN: 
                sAST = parseReturnStmt(); 
                break;
            default:
                sAST = parseExprStmt();
                break;
        }
        
        return sAST;
    }
    
    Stmt parseIfStmt() throws SyntaxError {
        Stmt sAST = null;
        SourcePosition position = new SourcePosition();
        start(position);
        
        match(Token.IF);
        match(Token.LPAREN);
        Expr expr = parseExpr();
        match(Token.RPAREN);
        Stmt stmt = parseStmt();
        if (currentToken.kind == Token.ELSE) {
            accept(); 
            Stmt stmtElse = parseStmt();
            finish(position);
            sAST = new IfStmt(expr, stmt, stmtElse, position);
        }
        else{
            finish(position);
            sAST = new IfStmt(expr, stmt, position);
        }
        
        return sAST;
    }
    
    Stmt parseForStmt() throws SyntaxError {
        Stmt sAST = null;
        SourcePosition position = new SourcePosition();
        start(position);
        
        match(Token.FOR);
        match(Token.LPAREN);
        
        Expr expr1 = null; Expr expr2 = null; Expr expr3 = null;
        if( currentToken.kind != Token.SEMICOLON ){
            expr1 = parseExpr();
        }else{
            expr1 = new EmptyExpr(dummyPos);
        }
        match(Token.SEMICOLON);
        if( currentToken.kind != Token.SEMICOLON ){
            expr2 = parseExpr();
        }else{
            expr2 = new EmptyExpr(dummyPos);
        }
        match(Token.SEMICOLON);
        if( currentToken.kind != Token.RPAREN ){
            expr3 = parseExpr();
        }else{
            expr3 = new EmptyExpr(dummyPos);
        }
        match(Token.RPAREN);
        Stmt stmt = parseStmt();
        
        finish(position);
        sAST = new ForStmt(expr1, expr2, expr3, stmt, position);
        
        return sAST;
    }
    
    Stmt parseWhileStmt() throws SyntaxError {
        Stmt sAST = null;
        SourcePosition position = new SourcePosition();
        start(position);
        
        match(Token.WHILE);
        match(Token.LPAREN);
        
        Expr expr = null;
        if( currentToken.kind != Token.RPAREN ){
            expr = parseExpr();
        }else{
            expr = new EmptyExpr(dummyPos);
        }
        
        match(Token.RPAREN);
        Stmt stmt = parseStmt();
        
        finish(position);
        sAST = new WhileStmt(expr, stmt, position);
        
        return sAST;
    }
    
    // break-stmt -> break ";"
    Stmt parseBreakStmt() throws SyntaxError {
        Stmt sAST = null;
        SourcePosition position = new SourcePosition();
        start(position);
        
        match(Token.BREAK);
        match(Token.SEMICOLON);
        
        finish(position);
        sAST = new BreakStmt(position);
        return sAST;
    }
    
    // continue-stmt -> continue ";"
    Stmt parseContinueStmt() throws SyntaxError {
        Stmt sAST = null;
        SourcePosition position = new SourcePosition();
        start(position);
        
        match(Token.CONTINUE);
        match(Token.SEMICOLON);
        
        finish(position);
        sAST = new ContinueStmt(position);
        return sAST;
    }
    
    Stmt parseReturnStmt() throws SyntaxError {
        Stmt sAST = null;
        SourcePosition position = new SourcePosition();
        start(position);
        
        match(Token.RETURN);
        
        Expr expr = null;
        if( currentToken.kind != Token.SEMICOLON ){
            expr = parseExpr();
        }else{
            expr = new EmptyExpr(dummyPos);
        }
        
        match(Token.SEMICOLON);
        
        finish(position);
        sAST = new ReturnStmt(expr, position);
        return sAST;
    }
    
    // expr-stmt -> expr? ";"
    Stmt parseExprStmt() throws SyntaxError {
        Stmt sAST = null;
        SourcePosition position = new SourcePosition();
        start(position);
        
        Expr expr = null;
        if( currentToken.kind != Token.SEMICOLON ){
            expr = parseExpr();
        }
        else{
            expr = new EmptyExpr(dummyPos);
        }
        
        match(Token.SEMICOLON);
        finish(position);
        sAST = new ExprStmt(expr, position);
        return sAST;
    }
    
    // ======================= PARAMETERS =======================
    List parseParaList() throws SyntaxError {
        List formalsAST = null;
        accept();
        if (currentToken.kind != Token.RPAREN){
            formalsAST = parseProperParaList();
        }
        else {
            formalsAST = new EmptyParaList(dummyPos);
        }
        match(Token.RPAREN);
        return formalsAST;
    }
    
    // proper-para-list -> para-decl ( "," para-decl )*
    List parseProperParaList() throws SyntaxError {
        List paraList = null;
        ParaDecl paradecl = null;
        
        SourcePosition position = new SourcePosition();
        start(position);
        
        paradecl = parseParaDecl();
        if (currentToken.kind == Token.COMMA) {
            accept();
            paraList = parseProperParaList();
            finish(position);
            paraList = new ParaList(paradecl, paraList, position);
        }
        else {
            finish(position);
            paraList = new ParaList(paradecl, new EmptyParaList(dummyPos), position);
        }
        return paraList;
    }
    
    // para-decl -> type declarator
    ParaDecl parseParaDecl() throws SyntaxError {
        ParaDecl paradecl = null;
        
        SourcePosition position = new SourcePosition();
        start(position);
        
        Type tAST = parseType( null );

        SourcePosition arrayPos = new SourcePosition();
        start(arrayPos);
        Ident ident = parseDeclarator();  
        
        //added to process array type
        Type arrayTypeTmp = parseArrayTypeIfItIs( tAST, ident );
        if( arrayTypeTmp != null ) tAST = arrayTypeTmp;

        finish(position);
        paradecl = new ParaDecl(tAST, ident, position);
        return paradecl;
    }
    
    List parseArgList() throws SyntaxError {
        accept(); // accept '('
        
        List argList = null;
        Arg arg = null;
        
        SourcePosition position = new SourcePosition();
        start(position);
        
        if (currentToken.kind != Token.RPAREN){
            arg = parseArg();
            if( currentToken.kind == Token.COMMA ){
                accept();
                argList = parseProperArgList();
                finish(position);
                argList = new ArgList(arg, argList, position);
            }
            else{
                finish(position);
                argList = new ArgList(arg, new EmptyArgList(dummyPos), position);
            }
        }
        else{
            argList = new EmptyArgList(dummyPos);
        }
        match(Token.RPAREN);
        
        return argList;
    }
    
    // proper-para-list -> para-decl ( "," para-decl )*
    List parseProperArgList() throws SyntaxError {
        List argList = null;
        Arg arg = null;
        
        SourcePosition position = new SourcePosition();
        start(position);
        
        arg = parseArg();
        if (currentToken.kind == Token.COMMA) {
            accept();
            argList = parseProperArgList();
            finish(position);
            argList = new ArgList(arg, argList, position);
        }
        else {
            finish(position);
            argList = new ArgList(arg, new EmptyArgList(dummyPos), position);
        }
        return argList;
    }
    Arg parseArg() throws SyntaxError {
        Arg arg = null;
        SourcePosition position = new SourcePosition();
        start(position);
        
        Expr expr = parseExpr();
        finish(position);
        arg = new Arg(expr, position);
        return arg;
    }
    
    // ======================= EXPRESSIONS ======================
    // expr -> assignment-expr
    Expr parseExpr() throws SyntaxError {
        Expr exprAST = null;
        exprAST = parseAssignExpr();
        return exprAST;
    }
    // assignment-expr -> ( cond-or-expr "=" )* cond-or-expr
    Expr parseAssignExpr() throws SyntaxError {
        Expr exprAST = null;
        SourcePosition addStartPos = new SourcePosition();
        start(addStartPos);
        
        exprAST = parseConOrExpr();
        if (currentToken.kind == Token.EQ) {
            Operator opAST = acceptOperator();
            Expr expr2 = parseAssignExpr();
            
            SourcePosition addPos = new SourcePosition();
            copyStart(addStartPos, addPos);
            finish(addPos);
         
            exprAST = new AssignExpr(exprAST, expr2, addPos);
        }
        
        return exprAST;
    }

    Expr parseConOrExpr() throws SyntaxError {
        Expr exprAST = null;
        SourcePosition addStartPos = new SourcePosition();
        start(addStartPos);
        
        exprAST = parseConAndExpr();
        while (currentToken.kind == Token.OROR) {
            Operator opAST = acceptOperator();
            Expr expr2 = parseConAndExpr();
            
            SourcePosition addPos = new SourcePosition();
            copyStart(addStartPos, addPos);
            finish(addPos);
            exprAST = new BinaryExpr(exprAST, opAST, expr2, addPos);
        }
        
        return exprAST;
    }
   
    Expr parseConAndExpr() throws SyntaxError {
        Expr exprAST = null;
        SourcePosition addStartPos = new SourcePosition();
        start(addStartPos);
        
        exprAST = parseEqualExpr();
        while (currentToken.kind == Token.ANDAND) {
            Operator opAST = acceptOperator();
            Expr expr2 = parseEqualExpr();
            
            SourcePosition addPos = new SourcePosition();
            copyStart(addStartPos, addPos);
            finish(addPos);
            exprAST = new BinaryExpr(exprAST, opAST, expr2, addPos);
        }
        
        return exprAST;
    }
   
    Expr parseEqualExpr() throws SyntaxError {
        Expr exprAST = null;
        SourcePosition addStartPos = new SourcePosition();
        start(addStartPos);
        
        exprAST = parseRealExpr();
        while (currentToken.kind == Token.EQEQ || currentToken.kind == Token.NOTEQ) {
            Operator opAST = acceptOperator();
            Expr expr2 = parseRealExpr();
            
            SourcePosition addPos = new SourcePosition();
            copyStart(addStartPos, addPos);
            finish(addPos);
            exprAST = new BinaryExpr(exprAST, opAST, expr2, addPos);
        }
        
        return exprAST;
    }
   
    Expr parseRealExpr() throws SyntaxError {
        Expr exprAST = null;
        SourcePosition addStartPos = new SourcePosition();
        start(addStartPos);
        
        exprAST = parseAdditiveExpr();
        while (currentToken.kind == Token.LT || currentToken.kind == Token.LTEQ || currentToken.kind == Token.GT || currentToken.kind == Token.GTEQ) {
            Operator opAST = acceptOperator();
            Expr expr2 = parseAdditiveExpr();
            
            SourcePosition addPos = new SourcePosition();
            copyStart(addStartPos, addPos);
            finish(addPos);
            exprAST = new BinaryExpr(exprAST, opAST, expr2, addPos);
        }
        
        return exprAST;
    }
  
    Expr parseAdditiveExpr() throws SyntaxError {
        Expr exprAST = null;
        SourcePosition addStartPos = new SourcePosition();
        start(addStartPos);

        exprAST = parseMultiplicativeExpr();
        while (currentToken.kind == Token.PLUS || currentToken.kind == Token.MINUS) {
            Operator opAST = acceptOperator();
            Expr e2AST = parseMultiplicativeExpr();

            SourcePosition addPos = new SourcePosition();
            copyStart(addStartPos, addPos);
            finish(addPos);
            exprAST = new BinaryExpr(exprAST, opAST, e2AST, addPos);
        }
        return exprAST;
    }
   
    Expr parseMultiplicativeExpr() throws SyntaxError {
        Expr exprAST = null;
        SourcePosition multStartPos = new SourcePosition();
        start(multStartPos);

        exprAST = parseUnaryExpr();
        while (currentToken.kind == Token.MULT || currentToken.kind == Token.DIV) {
            Operator opAST = acceptOperator();
            Expr e2AST = parseUnaryExpr();
            SourcePosition multPos = new SourcePosition();
            copyStart(multStartPos, multPos);
            finish(multPos);
            exprAST = new BinaryExpr(exprAST, opAST, e2AST, multPos);
        }
        return exprAST;
    }
  
    Expr parseUnaryExpr() throws SyntaxError {
        Expr exprAST = null;
        SourcePosition unaryPos = new SourcePosition();
        start(unaryPos);

        switch (currentToken.kind) {
            case Token.PLUS:
            case Token.MINUS:
            case Token.NOT:
                Operator opAST = acceptOperator();
                Expr e2AST = parseUnaryExpr();
                finish(unaryPos);
                exprAST = new UnaryExpr(opAST, e2AST, unaryPos);
                break;
            default:
                exprAST = parsePrimaryExpr();
                break;
        }
        return exprAST;
    }
    
    Expr parsePrimaryExpr() throws SyntaxError {
        Expr exprAST = null;
        SourcePosition primPos = new SourcePosition();
        start(primPos);
        switch (currentToken.kind) {
            case Token.ID:
                Ident iAST = parseIdent();
                finish(primPos);
                Var simVAST = new SimpleVar(iAST, primPos);
                
                if( currentToken.kind == Token.LPAREN ){
                    List argList = parseArgList();
                    finish(primPos);
                    exprAST = new CallExpr(iAST, argList, primPos);
                    break;
                }
                else if( currentToken.kind == Token.LBRACKET){
                    accept();
                    Expr expr = parseExpr();
                    match(Token.RBRACKET);
                    
                    finish(primPos);
                    exprAST = new ArrayExpr(simVAST, expr, primPos);
                    break;
                }
                else{
                    exprAST = new VarExpr(simVAST, primPos);
                    break;
                }
            case Token.LPAREN: 
                accept();
                exprAST = parseExpr();
                match(Token.RPAREN);
                break;
    
            case Token.INTLITERAL:
                IntLiteral intAST = parseIntLiteral();
                finish(primPos);
                exprAST = new IntExpr(intAST, primPos);
                break;
            case Token.FLOATLITERAL:
                FloatLiteral floatAST = parseFloatLiteral();
                finish(primPos);
                exprAST = new FloatExpr(floatAST, primPos);
                break;
            case Token.BOOLEANLITERAL:
                BooleanLiteral boolAST = parseBooleanLiteral();
                finish(primPos);
                exprAST = new BooleanExpr(boolAST, primPos);
                break;
            case Token.STRINGLITERAL:
                StringLiteral stringAST = parseStringLiteral();
                finish(primPos);
                exprAST = new StringExpr(stringAST, primPos);
                break;
            default:
                syntacticError("illegal primary expression", currentToken.spelling);
        }
        return exprAST;
    }

    // ========================== ID, OPERATOR and LITERALS
    // ========================

    Ident parseIdent() throws SyntaxError {
        Ident I = null;

        if (currentToken.kind == Token.ID) {
            previousTokenPosition = currentToken.position;
            String spelling = currentToken.spelling;
            I = new Ident(spelling, previousTokenPosition);
            currentToken = scanner.getToken();
        } else
            syntacticError("identifier expected here", "");
        return I;
    }

    // acceptOperator parses an operator, and constructs a leaf AST for it
    Operator acceptOperator() throws SyntaxError {
        Operator O = null;

        previousTokenPosition = currentToken.position;
        String spelling = currentToken.spelling;
        O = new Operator(spelling, previousTokenPosition);
        currentToken = scanner.getToken();
        return O;
    }

    IntLiteral parseIntLiteral() throws SyntaxError {
        IntLiteral IL = null;

        if (currentToken.kind == Token.INTLITERAL) {
            String spelling = currentToken.spelling;
            accept();
            IL = new IntLiteral(spelling, previousTokenPosition);
        } else
            syntacticError("integer literal expected here", "");
        return IL;
    }

    FloatLiteral parseFloatLiteral() throws SyntaxError {
        FloatLiteral FL = null;

        if (currentToken.kind == Token.FLOATLITERAL) {
            String spelling = currentToken.spelling;
            accept();
            FL = new FloatLiteral(spelling, previousTokenPosition);
        } else
            syntacticError("float literal expected here", "");
        return FL;
    }

    BooleanLiteral parseBooleanLiteral() throws SyntaxError {
        BooleanLiteral BL = null;

        if (currentToken.kind == Token.BOOLEANLITERAL) {
            String spelling = currentToken.spelling;
            accept();
            BL = new BooleanLiteral(spelling, previousTokenPosition);
        } else
            syntacticError("boolean literal expected here", "");
        return BL;
    }
    
    StringLiteral parseStringLiteral() throws SyntaxError {
        StringLiteral BL = null;

        if (currentToken.kind == Token.STRINGLITERAL) {
            String spelling = currentToken.spelling;
            accept();
            BL = new StringLiteral(spelling, previousTokenPosition);
        } else
            syntacticError("boolean literal expected here", "");
        return BL;
    }
}
