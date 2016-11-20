/***
 * *
 * * Recogniser.java            
 * *
 ***/

package VC.Recogniser;

import VC.Scanner.Scanner;
import VC.Scanner.SourcePosition;
import VC.Scanner.Token;
import VC.ErrorReporter;

public class Recogniser {

    private Scanner scanner;
    private ErrorReporter errorReporter;
    private Token currentToken;

    public Recogniser(Scanner lexer, ErrorReporter reporter) {
        scanner = lexer;
        errorReporter = reporter;

        currentToken = scanner.getToken();
    }

    // match checks to see f the current token matches tokenExpected.
    // If so, fetches the next token.
    // If not, reports a syntactic error.

    void match(int tokenExpected) throws SyntaxError {
        if (currentToken.kind == tokenExpected) {
            currentToken = scanner.getToken();
        } else {
            syntacticError("\"%\" expected here", Token.spell(tokenExpected));
        }
    }

    // accepts the current token and fetches the next
    void accept() {
        currentToken = scanner.getToken();
    }

    void syntacticError(String messageTemplate, String tokenQuoted)
        throws SyntaxError {
            SourcePosition pos = currentToken.position;
            errorReporter.reportError(messageTemplate, tokenQuoted, pos);
            throw (new SyntaxError());
    }

    // ========================== PROGRAMS ========================

    public void parseProgram() {
        try {
            while( true ){
                parseFuncOrVarDecl();
                if( currentToken.kind == Token.EOF ) break;
            }
        } catch (SyntaxError s) { }
    }

    // ========================== DECLARATIONS ========================
    // Original function is only for parseFuncDecl()
    void parseFuncOrVarDecl() throws SyntaxError {
        parseType();
        parseIdent();
        
        if( currentToken.kind == Token.LPAREN ){  //function declaration
            parseParaList();
            parseCompoundStmt();
        }
        else{                                     //variable declaration
            parseInitDeclaratorListBesidesFirstIdent();
            match(Token.SEMICOLON);
        }
        
    }
    
    void parseInitDeclaratorListBesidesFirstIdent() throws SyntaxError {
       parseInitDeclaratorBesidesFirstIdent();
       while( currentToken.kind == Token.COMMA ){
           accept();
           parseInitDeclarator();
       }
    }
    void parseInitDeclaratorBesidesFirstIdent() throws SyntaxError {
        parseDeclaratorBesidesIdent();
        if( currentToken.kind == Token.EQ ){
            acceptOperator();
            parseInitialiser();
        }
    }
    
    // Special case for func-decl LL(2)
    void parseDeclaratorBesidesIdent() throws SyntaxError {
        if (currentToken.kind == Token.LBRACKET) {
            accept();
            if (currentToken.kind == Token.INTLITERAL) parseIntLiteral();
            match(Token.RBRACKET);
        }
    }
    
    //initialiser -> expr 
    //             | "{" expr ( "," expr )* "}"
    void parseInitialiser() throws SyntaxError {
        if( currentToken.kind == Token.LCURLY ){
            accept();
            parseExpr();
            while( currentToken.kind == Token.COMMA ){
                accept(); parseExpr();
            }
            match(Token.RCURLY);
        }
        else parseExpr();
    }
    
    void parseInitDeclarator() throws SyntaxError {
        parseDeclarator();
        if( currentToken.kind == Token.EQ ){
            acceptOperator();
            parseInitialiser();
        }
    }
    
    // identifier ("[" INTLITERAL? "]")?
    void parseDeclarator() throws SyntaxError {
        parseIdent();
        if (currentToken.kind == Token.LBRACKET) {
            accept();
            if (currentToken.kind == Token.INTLITERAL) parseIntLiteral();
            match(Token.RBRACKET);
        }
    }
    

    // ======================= STATEMENTS ==============================

    // para-list -> "(" proper-para-list? ")"
    void parseParaList() throws SyntaxError {
        accept();
        if (currentToken.kind != Token.RPAREN)
            parseProperParaList();
        match(Token.RPAREN);
    }

    // proper-para-list -> para-decl ( "," para-decl )*
    void parseProperParaList() throws SyntaxError {
        parseParaDecl();
        while (currentToken.kind == Token.COMMA) {
            accept();
            parseParaDecl();
        }
    }

    // para-decl -> type declarator
    void parseParaDecl() throws SyntaxError {
        parseType();
        parseDeclarator();
    }

    // void | boolean | int | float
    void parseType() throws SyntaxError {
        switch (currentToken.kind) {
        case Token.VOID:
            match(Token.VOID);
            break;
        case Token.BOOLEAN:
            match(Token.BOOLEAN);
            break;
        case Token.INT:
            match(Token.INT);
            break;
        case Token.FLOAT:
            match(Token.FLOAT);
            break;
        default:
            syntacticError("\"%\" wrong result type for a function", currentToken.spelling);
            break;
        }
        // default case actually means it already has syntatically error now
    }

    // compound-stmt -> "{" var-decl* stmt* "}"
    void parseCompoundStmt() throws SyntaxError {
        match(Token.LCURLY);
        parseVarDeclaratorList();
        parseStmtList();
        match(Token.RCURLY);
    }

    void parseVarDeclaratorList() throws SyntaxError {
        while (currentToken.kind == Token.VOID
                || currentToken.kind == Token.BOOLEAN
                || currentToken.kind == Token.INT
                || currentToken.kind == Token.FLOAT) {
            parseVarDeclarator();
        }
    }

    void parseVarDeclarator() throws SyntaxError {
        parseType();
        parseIdent();
        parseInitDeclaratorListBesidesFirstIdent();
        match(Token.SEMICOLON);
    }

    // Here, a new nontermial has been introduced to define { stmt } *
    void parseStmtList() throws SyntaxError {
        while (currentToken.kind != Token.RCURLY)
            parseStmt();
    }

    // stmt -> compound-stmt
    // | if-stmt
    // | for-stmt
    // | while-stmt
    // | break-stmt
    // | continue-stmt
    // | return-stmt
    // | expr-stmt
    void parseStmt() throws SyntaxError {
        switch (currentToken.kind) {
        case Token.LCURLY:
            parseCompoundStmt();
            break;
        case Token.IF:
            parseIfStmt();
            break;
        case Token.FOR:
            parseForStmt();
            break;
        case Token.WHILE: 
            parseWhileStmt(); 
            break;
        case Token.BREAK:
            parseBreakStmt();
            break;
        case Token.CONTINUE:
            parseContinueStmt();
            break;
        case Token.RETURN: 
            parseReturnStmt(); 
            break;
        default:
            parseExprStmt();
            break;
        }
    }

    void parseIfStmt() throws SyntaxError {
        match(Token.IF);
        match(Token.LPAREN);
        parseExpr();
        match(Token.RPAREN);
        parseStmt();
        if (currentToken.kind == Token.ELSE) {
            accept(); parseStmt();
        }
    }

    void parseForStmt() throws SyntaxError {
        match(Token.FOR);
        match(Token.LPAREN);
        if( currentToken.kind != Token.SEMICOLON ) parseExpr();
        match(Token.SEMICOLON);
        if( currentToken.kind != Token.SEMICOLON ) parseExpr();
        match(Token.SEMICOLON);
        if( currentToken.kind != Token.RPAREN ) parseExpr();
        match(Token.RPAREN);
        parseStmt();
    }
    
    void parseWhileStmt() throws SyntaxError {
        match(Token.WHILE);
        match(Token.LPAREN);
        parseExpr();
        match(Token.RPAREN);
        parseStmt();
    }

    // break-stmt -> break ";"
    void parseBreakStmt() throws SyntaxError {
        match(Token.BREAK);
        match(Token.SEMICOLON);
    }

    // continue-stmt -> continue ";"
    void parseContinueStmt() throws SyntaxError {
        match(Token.CONTINUE);
        match(Token.SEMICOLON);
    }
    
    void parseReturnStmt() throws SyntaxError {
        match(Token.RETURN);
        if( currentToken.kind != Token.SEMICOLON ) parseExpr();
        match(Token.SEMICOLON);
    }

    // expr-stmt -> expr? ";"
    void parseExprStmt() throws SyntaxError {
        if( currentToken.kind != Token.SEMICOLON ){
            parseExpr();
        }
        match(Token.SEMICOLON);
    }

    // ======================= IDENTIFIERS ======================

    // Call parseIdent rather than match(Token.ID).
    // In Assignment 3, an Identifier node will be constructed in here.

    void parseIdent() throws SyntaxError {
        if (currentToken.kind == Token.ID) {
            currentToken = scanner.getToken();
        } else
            syntacticError("identifier expected here", "");
    }

    // ======================= OPERATORS ======================

    // Call acceptOperator rather than accept().
    // In Assignment 3, an Operator Node will be constructed in here.

    void acceptOperator() throws SyntaxError {
        currentToken = scanner.getToken();
    }

    // ======================= EXPRESSIONS ======================

    // expr -> assignment-expr
    void parseExpr() throws SyntaxError {
        parseAssignExpr();
    }

    // assignment-expr -> ( cond-or-expr "=" )* cond-or-expr
    void parseAssignExpr() throws SyntaxError {
        parseConOrExpr();
        while (currentToken.kind == Token.EQ) {
            acceptOperator();
            parseConOrExpr();
        }
    }

    // cond-or-expr -> cond-and-expr ( "||" cond-and-expr )* (modified version
    // to eliminate left-recursive situation)
    // Unmodified version below:
    // cond-or-expr -> cond-and-expr
    // | cond-or-expr "||" cond-and-expr
    void parseConOrExpr() throws SyntaxError {
        parseConAndExpr();
        while (currentToken.kind == Token.OROR) {
            acceptOperator();
            parseConAndExpr();
        }
    }

    // cond-and-expr -> equality-expr ( "&&" equality-expr )* (modified version
    // to eliminate left-recursive situation)
    // Unmodified version below:
    // cond-and-expr -> equality-expr
    // | cond-and-expr "&&" equality-expr
    void parseConAndExpr() throws SyntaxError {
        parseEqualExpr();
        while (currentToken.kind == Token.ANDAND) {
            acceptOperator();
            parseEqualExpr();
        }
    }

    // equality-expr -> (rel-expr "=="||"!=")* rel-expr (modified version to
    // eliminate left-recursive situation)
    // Unmodified version below:
    // equality-expr -> rel-expr
    // | equality-expr "==" rel-expr
    // | equality-expr "!=" rel-expr
    void parseEqualExpr() throws SyntaxError {
        parseRealExpr();
        while (currentToken.kind == Token.EQEQ
                || currentToken.kind == Token.NOTEQ) {
            acceptOperator();
            parseRealExpr();
        }
    }

    // rel-expr -> (additive-expr "<"||"<="||">"||">=")* additive-expr (modified
    // version to eliminate left-recursive situation)
    // Unmodified version below:
    // rel-expr -> additive-expr
    // | rel-expr "<" additive-expr
    // | rel-expr "<=" additive-expr
    // | rel-expr ">" additive-expr
    // | rel-expr ">=" additive-expr
    void parseRealExpr() throws SyntaxError {
        parseAdditiveExpr();
        while (currentToken.kind == Token.LT || currentToken.kind == Token.LTEQ
                || currentToken.kind == Token.GT
                || currentToken.kind == Token.GTEQ) {
            acceptOperator();
            parseAdditiveExpr();
        }
    }

    // additive-expr -> (multiplicative-expr "+"||"-")* multiplicative-expr
    // (modified version to eliminate left-recursive situation)
    // Unmodified version below:
    // additive-expr -> multiplicative-expr
    // | additive-expr "+" multiplicative-expr
    // | additive-expr "-" multiplicative-expr
    //
    void parseAdditiveExpr() throws SyntaxError {
        parseMultiplicativeExpr();
        while (currentToken.kind == Token.PLUS
                || currentToken.kind == Token.MINUS) {
            acceptOperator();
            parseMultiplicativeExpr();
        }
    }

    // multiplicative-expr -> (unary-expr "*"||"/")* unary-expr (modified
    // version to eliminate left-recursive situation)
    // Unmodified version below:
    // multiplicative-expr -> unary-expr
    // | multiplicative-expr "*" unary-expr
    // | multiplicative-expr "/" unary-expr
    void parseMultiplicativeExpr() throws SyntaxError {
        parseUnaryExpr();
        while (currentToken.kind == Token.MULT
                || currentToken.kind == Token.DIV) {
            acceptOperator();
            parseUnaryExpr();
        }
    }

    // unary-expr -> "+" unary-expr
    // | "-" unary-expr
    // | "!" unary-expr
    // | primary-expr
    void parseUnaryExpr() throws SyntaxError {
        switch (currentToken.kind) {
        case Token.PLUS:
            acceptOperator();
            parseUnaryExpr();
            break; // Q1: what if +++++++5
        case Token.MINUS:
            acceptOperator();
            parseUnaryExpr();
            break;
        case Token.NOT:
            acceptOperator();
            parseUnaryExpr();
            break;
        default:
            parsePrimaryExpr();
            break;
        }
    }

    // primary-expr -> identifier ( arg-list?|"[" expr "]" ) (modified version
    // to eliminate common-prefix situation)
    // | "(" expr ")"
    // | INTLITERAL
    // | FLOATLITERAL
    // | BOOLLITERAL
    // | STRINGLITERAL
    void parsePrimaryExpr() throws SyntaxError {
        switch (currentToken.kind) {
        case Token.ID:
            accept(); // parseIdent(); ??
            if (currentToken.kind == Token.LPAREN) parseArgList(); // already match ')'
            else if (currentToken.kind == Token.LBRACKET) {
                accept();
                parseExpr();
                match(Token.RBRACKET);
            }
            break;
        case Token.LPAREN:
            accept();
            parseExpr();
            match(Token.RPAREN);
            break;
        case Token.INTLITERAL:
            parseIntLiteral();
            break;
        case Token.FLOATLITERAL:
            parseFloatLiteral();
            break;
        case Token.BOOLEANLITERAL:
            parseBooleanLiteral();
            break;
        case Token.STRINGLITERAL:
            parseStringLiteral();
            break;
        default:
            syntacticError("illegal parimary expression", currentToken.spelling);
        }
    }

    void parseArgList() throws SyntaxError {
        accept(); // accept '('
        if (currentToken.kind != Token.RPAREN)
            parseProperArgList();
        match(Token.RPAREN);
    }

    void parseProperArgList() throws SyntaxError {
        parseArg();
        while (currentToken.kind == Token.COMMA) {
            accept();
            parseArg();
        }
    }

    void parseArg() throws SyntaxError {
        parseExpr();
    }

    // ========================== LITERALS ========================

    // Call these methods rather than accept(). In Assignment 3,
    // literal AST nodes will be constructed inside these methods.

    void parseIntLiteral() throws SyntaxError {
        if (currentToken.kind == Token.INTLITERAL) {
            currentToken = scanner.getToken();
        } else
            syntacticError("integer literal expected here", "");
    }

    void parseFloatLiteral() throws SyntaxError {
        if (currentToken.kind == Token.FLOATLITERAL) {
            currentToken = scanner.getToken();
        } else
            syntacticError("float literal expected here", "");
    }

    void parseBooleanLiteral() throws SyntaxError {
        if (currentToken.kind == Token.BOOLEANLITERAL) {
            currentToken = scanner.getToken();
        } else
            syntacticError("boolean literal expected here", "");
    }

    void parseStringLiteral() throws SyntaxError {
        if (currentToken.kind == Token.STRINGLITERAL) {
            currentToken = scanner.getToken();
        } else
            syntacticError("boolean literal expected here", "");
    }
}
