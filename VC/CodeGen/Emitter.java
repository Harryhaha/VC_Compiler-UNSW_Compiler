/*
 * Emitter.java    15 -|- MAY -|- 2016
 * Jingling Xue, School of Computer Science, UNSW, Australia
 */

// A new frame object is created for every function just before the
// function is being translated in visitFuncDecl.
//
// All the information about the translation of a function should be
// placed in this Frame object and passed across the AST nodes as the
// 2nd argument of every visitor method in Emitter.java.

package VC.CodeGen;

import java.awt.print.Printable;

import VC.ASTs.*;
import VC.ErrorReporter;
import VC.StdEnvironment;

public final class Emitter implements Visitor {

  private ErrorReporter errorReporter;
  private String inputFilename;
  private String classname;
  private String outputFilename;

  public Emitter(String inputFilename, ErrorReporter reporter) {
    this.inputFilename = inputFilename;
    errorReporter = reporter;
    
    int i = inputFilename.lastIndexOf('.');
    if (i > 0)
      classname = inputFilename.substring(0, i);
    else
      classname = inputFilename;
    
  }

  // PRE: ast must be a Program node

  public final void gen(AST ast) {
    ast.visit(this, null); 
    JVM.dump(classname + ".j");
  }
    
  // Programs
  public Object visitProgram(Program ast, Object o) {
     /** This method works for scalar variables only. You need to modify
         it to handle all array-related declarations and initialisations.
      **/ 

    // Generates the default constructor initialiser 
    emit(JVM.CLASS, "public", classname);   // .class public test
    emit(JVM.SUPER, "java/lang/Object");    // .super "java/lang/Object"

    emit("");

    // Three subpasses:

    // (1) Generate .field definition statements since
    //     these are required to appear before method definitions
    List list = ast.FL;
    while (!list.isEmpty()) {
        DeclList dlAST = (DeclList) list;
        if (dlAST.D instanceof GlobalVarDecl) {
            GlobalVarDecl vAST = (GlobalVarDecl) dlAST.D;
        
            if( vAST.T.isArrayType() ){
//              emit(JVM.STATIC_FIELD, vAST.I.spelling, VCtoJavaType( ((ArrayType)vAST.T).T ));
                emit(JVM.STATIC_FIELD, vAST.I.spelling, ((ArrayType)vAST.T).toString()  );
            }
            else{
                emit(JVM.STATIC_FIELD, vAST.I.spelling, VCtoJavaType(vAST.T));
            }
            
        }
        list = dlAST.DL;
    }
    emit("");

    // (2) Generate <clinit> for global variables (assumed to be static)
    emit("; standard class static initializer ");
    emit(JVM.METHOD_START, "static <clinit>()V");
    emit("");

    // create a Frame for <clinit>
    Frame frame = new Frame(false);

    list = ast.FL;
    while (!list.isEmpty()) {
        DeclList dlAST = (DeclList) list;
        if (dlAST.D instanceof GlobalVarDecl) {
            GlobalVarDecl vAST = (GlobalVarDecl) dlAST.D;
            
            if (!vAST.E.isEmptyExpr()) {
                if( vAST.T.isArrayType() ){
                    // deal with array-related initialization
                    ArrayType arrayType = (ArrayType)vAST.T;
                    Type type = arrayType.T;
                    int arraySize = Integer.parseInt( ((IntExpr)arrayType.E).IL.spelling );
                    
                    emitICONST( arraySize );
                    emit(JVM.NEWARRAY, getTypeString(type) );
                    frame.push();
                    
                    vAST.E.visit(this, frame);  // go to visitInitExpr 
                }
                else {
                    vAST.E.visit(this, frame);  // non-array initialization
                }
                
            } else {
                // According to spec, so we assume this else would not
                // happen, since it says all glocal variable should be initialized.
                if( vAST.T.isArrayType() ){
                    // deal with array-related initialization
                    ArrayType arrayType = (ArrayType)vAST.T;
                    Type type = arrayType.T;
                    int arraySize = Integer.parseInt( ((IntExpr)arrayType.E).IL.spelling );
                    
                    emitICONST( arraySize );
                    emit(JVM.NEWARRAY, getTypeString(type) );
                    // frame.push();
                } 
                else if (vAST.T.equals(StdEnvironment.floatType)){
                    emit(JVM.FCONST_0);
                }
                else{
                    emit(JVM.ICONST_0);
                }
                frame.push();
            }
            emitPUTSTATIC(VCtoJavaType(vAST.T), vAST.I.spelling); 
            frame.pop();
        } 
        list = dlAST.DL;
    }
   
    emit("");
    emit("; set limits used by this method");
    emit(JVM.LIMIT, "locals", frame.getNewIndex());

    emit(JVM.LIMIT, "stack", frame.getMaximumStackSize());
    emit(JVM.RETURN);
    emit(JVM.METHOD_END, "method");

    emit("");

    // (3) Generate Java bytecode for the VC program

    emit("; standard constructor initializer ");
    emit(JVM.METHOD_START, "public <init>()V");
    emit(JVM.LIMIT, "stack 1");
    emit(JVM.LIMIT, "locals 1");
    emit(JVM.ALOAD_0);
    emit(JVM.INVOKESPECIAL, "java/lang/Object/<init>()V");
    emit(JVM.RETURN);
    emit(JVM.METHOD_END, "method");

    return ast.FL.visit(this, o);
  }
  
  public String getTypeString( Type type ){
      if( type.isFloatType() ){
          return "float";
      } else if ( type.isIntType() ){
          return "int";
      } else if ( type.isBooleanType() ){
          return "boolean";
      } else if ( type.isStringType() ){
          return "string";
      } else {   // this would not happen 
          return null;
      }
  }
  

  // Statements
  public Object visitStmtList(StmtList ast, Object o) {
    ast.S.visit(this, o);
    ast.SL.visit(this, o);
    return null;
  }
  
  public Object visitIfStmt(IfStmt ast, Object o) {
      Frame frame = (Frame) o;
      
      String falseLabel = frame.getNewLabel();
      String nextLabel = frame.getNewLabel(); 
      
//      String nextLabel = ""; //if no else branch, the label should not incremented by 1
//      String nextLabel = frame.getNewLabel(); 
      
      ast.E.visit(this, o);
      
      emit( JVM.IFEQ, falseLabel);  //go to false
      frame.pop();                  //pop the last operator
      
      ast.S1.visit(this, o);
      
      //if the ifstmt has else branch, then add these:
      if( !(ast.S2 instanceof EmptyStmt) ){
//          nextLabel = frame.getNewLabel(); 
          emit( JVM.GOTO, nextLabel );
      }
      
      emit( falseLabel + ":" );
      
      if( !(ast.S2 instanceof EmptyStmt) ){
          ast.S2.visit(this, o);
          emit( nextLabel + ":" );
      }
      
      return null;
  }
  
  public Object visitWhileStmt(WhileStmt ast, Object o) {
      Frame frame = (Frame) o;
      
      String iterLabel = frame.getNewLabel();
      String skipLabel = frame.getNewLabel();
      
//      frame.scopeStart.push( iterLabel );
//      frame.scopeEnd.push( skipLabel );    
      
      frame.conStack.push( iterLabel );
      frame.brkStack.push( skipLabel );  
      
      emit( iterLabel + ":" );
      ast.E.visit(this, o);
      
      emit( JVM.IFEQ, skipLabel);  //go out the while loop
      frame.pop();
      
      ast.S.visit(this, o);
      emit( JVM.GOTO, iterLabel );
      
      emit( skipLabel + ":" );
      // already jump out the while loop
      
      frame.conStack.pop();
      frame.brkStack.pop();  
      
//      frame.scopeStart.pop();
//      frame.scopeEnd.pop();
      
      return null;
  }
  
  public Object visitForStmt(ForStmt ast, Object o) {
      Frame frame = (Frame) o;
      String iterLabel = frame.getNewLabel();
      String additionalLabel = frame.getNewLabel(); //ADDED
      String skipLabel = frame.getNewLabel();
      
//      frame.scopeStart.push( iterLabel );
//      frame.scopeEnd.push( skipLabel );    
      
//      frame.conStack.push( iterLabel );
      frame.conStack.push( additionalLabel );
      frame.brkStack.push( skipLabel );  
      
      //     [[E1]]
      // L1:
      //     [[E2]]
      //     ifeq L3
      //     [[S]]   //if it is continue, should go to L2 anyway
      //     goto L2
      // L2:            ADDED  
      //     [[E3]]
      //     goto L1
      // L3:
      
      ast.E1.visit(this, o);       // iteration initialization
      emit( iterLabel + ":" );
      
      if( ast.E2 instanceof EmptyExpr ){
          emit( JVM.ICONST_1 ); 
      }else{
          ast.E2.visit(this, o);
      }
      
      emit( JVM.IFEQ, skipLabel);  //go out the for loop
      frame.pop();
      ast.S.visit(this, o);
      emit( JVM.GOTO, additionalLabel );
      
      emit( additionalLabel + ":" );
      ast.E3.visit(this, o);
      emit( JVM.GOTO, iterLabel );
      
      emit( skipLabel + ":" );
      
      frame.conStack.pop();
      frame.brkStack.pop();  
      
//      frame.scopeStart.pop();
//      frame.scopeEnd.pop();
      
      return null;
  }
  
  public Object visitBreakStmt(BreakStmt ast, Object o){
      Frame frame = (Frame) o;
      String skipLabel = frame.brkStack.peek();
      emit( JVM.GOTO, skipLabel );
      return null;
  }
  
  public Object visitContinueStmt(ContinueStmt ast, Object o){
      Frame frame = (Frame) o;
      String iterLabel = frame.conStack.peek();
      emit( JVM.GOTO, iterLabel );
      return null;
  }

  public Object visitCompoundStmt(CompoundStmt ast, Object o) {
    Frame frame = (Frame) o; 

    String scopeStart = frame.getNewLabel();
    String scopeEnd = frame.getNewLabel();
    frame.scopeStart.push(scopeStart);
    frame.scopeEnd.push(scopeEnd);      //scope ++
    
    emit(scopeStart + ":");
    if (ast.parent instanceof FuncDecl) {
      if (((FuncDecl) ast.parent).I.spelling.equals("main")) {
        emit(JVM.VAR, "0 is argv [Ljava/lang/String; from " + (String) frame.scopeStart.peek() + " to " +  (String) frame.scopeEnd.peek());
        emit(JVM.VAR, "1 is vc$ L" + classname + "; from " + (String) frame.scopeStart.peek() + " to " +  (String) frame.scopeEnd.peek());
        // Generate code for the initialiser vc$ = new classname();
        emit(JVM.NEW, classname);
        emit(JVM.DUP);
        frame.push(2);
        emit("invokenonvirtual", classname + "/<init>()V");
        frame.pop();
        emit(JVM.ASTORE_1);
        frame.pop();
      } else {
        emit(JVM.VAR, "0 is this L" + classname + "; from " + (String) frame.scopeStart.peek() + " to " +  (String) frame.scopeEnd.peek());
        ((FuncDecl) ast.parent).PL.visit(this, o);
      }
    }
    ast.DL.visit(this, o);
    ast.SL.visit(this, o);
    emit(scopeEnd + ":");

    frame.scopeStart.pop();
    frame.scopeEnd.pop();
    
    return null;
  }

  public Object visitReturnStmt(ReturnStmt ast, Object o) {
    Frame frame = (Frame)o;
/*
  int main() { return 0; } must be interpretted as 
  public static void main(String[] args) { return ; }
  Therefore, "return expr", if present in the main of a VC program
  must be translated into a RETURN rather than IRETURN instruction.
*/

      if (frame.isMain())  {
          emit(JVM.RETURN);
          return null;
      }

      // Your other code goes here
      ast.E.visit(this, o);
      
      if( ast.E.type.isFloatType() ){
          emit( JVM.FRETURN );
      } else if( ast.E.type.isBooleanType() || ast.E.type.isIntType() ){
          emit( JVM.IRETURN );
      } 
      
      if( ast.E.type.isFloatType() || ast.E.type.isIntType() || ast.E.type.isBooleanType() ){
          frame.pop();
      }
      return null;
  }
  
  public Object visitExprStmt(ExprStmt ast, Object o) {
      Frame frame = (Frame) o;
      
      ast.E.visit(this, o);
      
      //insert pop instruction if it has a value left on the stack
      Expr expr = ast.E;
      if( expr instanceof IntExpr        ||
          expr instanceof FloatExpr      ||
          expr instanceof BooleanExpr    ||
          expr instanceof StringExpr ){
          emit( JVM.POP );
          frame.pop();
      }else if( expr instanceof BinaryExpr ){
          emit( JVM.POP );
          frame.pop();
      }else if( expr instanceof UnaryExpr ){
          emit( JVM.POP );
          frame.pop();
      }else if( expr instanceof ArrayExpr ){
          emit( JVM.POP );
          frame.pop();
      }else if( expr instanceof VarExpr ){
          emit( JVM.POP );
          frame.pop();
      }else if( expr instanceof CallExpr ){
          CallExpr callExpr = (CallExpr) expr;
          FuncDecl funcDecl = (FuncDecl) callExpr.I.decl;
          if( !funcDecl.T.isVoidType() ){
              emit( JVM.POP );//pop if the return type is not void
              frame.pop();
          }
      }
      
      return null;
  }

  //ADDED
  public Object visitEmptyExprList(EmptyExprList ast, Object o) {
      return null;
  }

  public Object visitEmptyStmtList(EmptyStmtList ast, Object o) {
    return null;
  }

  public Object visitEmptyCompStmt(EmptyCompStmt ast, Object o) {
    return null;
  }

  public Object visitEmptyStmt(EmptyStmt ast, Object o) {
    return null;
  }

  // Expressions
  //ADDED
  public Object visitBinaryExpr(BinaryExpr ast, Object o){
      Frame frame = (Frame) o;
      String op = ast.O.spelling;
      
      // add something here
      if( op.equals("i+") ){
          ast.E1.visit(this, o);
          ast.E2.visit(this, o);
          emit( JVM.IADD );
          frame.pop();
      } else if( op.equals("f+") ){
          ast.E1.visit(this, o);
          ast.E2.visit(this, o);
          emit( JVM.FADD );
          frame.pop();
      } else if( op.equals("i-") ){
          ast.E1.visit(this, o);
          ast.E2.visit(this, o);
          emit( JVM.ISUB );
          frame.pop();
      } else if( op.equals("f-") ){
          ast.E1.visit(this, o);
          ast.E2.visit(this, o);
          emit( JVM.FSUB );
          frame.pop();
      } else if( op.equals("i*") ){
          ast.E1.visit(this, o);
          ast.E2.visit(this, o);
          emit( JVM.IMUL );
          frame.pop();
      } else if( op.equals("f*") ){
          ast.E1.visit(this, o);
          ast.E2.visit(this, o);
          emit( JVM.FMUL );
          frame.pop();
      } else if( op.equals("i/") ){
          ast.E1.visit(this, o);
          ast.E2.visit(this, o);
          emit( JVM.IDIV );
          frame.pop();
      } else if( op.equals("f/") ){
          ast.E1.visit(this, o);
          ast.E2.visit(this, o);
          emit( JVM.FDIV );
          frame.pop();
          
      } else if( op.equals("i&&") ){
          String falseLabel = frame.getNewLabel();
          String nextLabel = frame.getNewLabel();
          ast.E1.visit(this, o);
          emit( JVM.IFEQ, falseLabel );
          frame.pop();
          
          ast.E2.visit(this, o);
          emit( JVM.IFEQ, falseLabel );
          frame.pop();
          
          emit( JVM.ICONST_1 );         //true
          emit( JVM.GOTO, nextLabel );
          emit( falseLabel + ":" );
          emit( JVM.ICONST_0 );         //false
          frame.push();
          emit( nextLabel + ":" );
      } else if( op.equals("i||") ){  //can only be boolean value
          String falseLabel = frame.getNewLabel();
          String nextLabel = frame.getNewLabel();
          ast.E1.visit(this, o);
          emit( JVM.IFNE, falseLabel );
          frame.pop();
          ast.E2.visit(this, o);
          emit( JVM.IFNE, falseLabel );
          frame.pop();
          emit( JVM.ICONST_0 );
          emit( JVM.GOTO, nextLabel );
          emit( falseLabel + ":" );
          emit( JVM.ICONST_1 );
          frame.push();
          emit( nextLabel + ":" );
      } else if( op.equals("i!=") ||
                 op.equals("i==") ||
                 op.equals("i<")  ||
                 op.equals("i<=") ||
                 op.equals("i>")  ||
                 op.equals("i>=")
              ){
          ast.E1.visit(this, o);
          ast.E2.visit(this, o);
          emitIF_ICMPCOND( op, frame );
      } else if( op.equals("f!=") ||
                 op.equals("f==") ||
                 op.equals("f<")  ||
                 op.equals("f<=") ||
                 op.equals("f>")  ||
                 op.equals("f>=")
              ){    
          ast.E1.visit(this, o);
          ast.E2.visit(this, o);
          emitFCMP( op, frame );
      }
      
      return null; 
  }
  
  public Object visitUnaryExpr(UnaryExpr ast, Object o){
      Frame frame = (Frame) o;
      
      if( ast.O.spelling.equals("i!") ){
          String falseLabel = frame.getNewLabel();
          String nextLabel = frame.getNewLabel();
          ast.E.visit(this, o);
          emit( JVM.IFEQ, falseLabel );
          frame.pop();
          emit( JVM.ICONST_0 );
          emit( JVM.GOTO, nextLabel );
          emit( falseLabel + ":" );
          emit( JVM.ICONST_1 );
          frame.push();
          emit( nextLabel + ":" );
      }
      else if( ast.O.spelling.equals("i+") || ast.O.spelling.equals("f+") ){
          ast.E.visit(this, o);
          // ....... anything else ?
      }
      // - (float/int):
      else if( ast.O.spelling.equals("i-") ){
          ast.E.visit(this, o);
          emit( JVM.INEG );
      } else if ( ast.O.spelling.equals("f-") ){
          ast.E.visit(this, o);
          emit( JVM.FNEG );
      }
      // below is to insert i2f type coersion instruction if it is.
      else if( ast.O.spelling.equals("i2f") ){
          ast.E.visit(this, o);
          emit( JVM.I2F );
      }
      return null;
  }
  
  // store instruction for array(float/int), float, int
  public void storeInstForTypes( int index, Type type, Frame frame, boolean arrayFlag){
      if ( type.equals(StdEnvironment.floatType) ) {
          if ( arrayFlag == true ){
              emit( JVM.FASTORE );
          }
          else if ( index >= 0 && index <= 3 ) {
              emit( JVM.FSTORE + "_" + index ); 
          } else {
              emit(JVM.FSTORE, index); 
          }
        
          frame.pop();
      } else {
          if ( arrayFlag == true ){
              if( type.isIntType() ) emit( JVM.IASTORE );
              if( type.isBooleanType()) emit( JVM.BASTORE );
          }
          else if ( index >= 0 && index <= 3) {
              emit(JVM.ISTORE + "_" + index); 
          } else {
              emit(JVM.ISTORE, index); 
          }
          
          frame.pop();
      }
  }
  public Object visitAssignExpr(AssignExpr ast, Object o){
      Frame frame = (Frame) o; 
      
      // Be careful here
      if( ast.E1 instanceof ArrayExpr ){
          ArrayExpr arrayExpr = (ArrayExpr) ast.E1;
          SimpleVar var = (SimpleVar) arrayExpr.V;
          Decl decl = (Decl)var.I.decl;
          
          // wrong below since in expression, the array subscribe could be any expr
//          IntExpr intExpr = (IntExpr) arrayExpr.E;
//          int arrayIndex = Integer.parseInt(intExpr.IL.spelling);
         // wrong above since in expression, the array subscribe could be any expr
          
          ArrayType arrayType = (ArrayType) decl.T;
          Type type = arrayType.T;
          
          int index = decl.index;
          
          if ( index >= 0 && index <= 3) {
              emit(JVM.ALOAD + "_" + index); 
          } else {
              emit(JVM.ALOAD, index); 
          }
          frame.push();
          
          arrayExpr.E.visit( this, o ); //inside will push/load the expr
//          emitICONST( arrayIndex );
         
          ast.E2.visit(this, o);  // already push the right side expr
          // notice: within E2.visit, i2f type coercion would be done if it is(within unaryExpr) 
          
          if( decl instanceof GlobalVarDecl ){
              emitPUTSTATIC( arrayType.toString(), var.I.spelling );
              frame.pop();
          }else{
              // store instruction for array(float/int), float, int
              storeInstForTypes( index, type, frame, true );
//            emitFSTORE(identifier); currently not use this method
          }
          
          // need to dup as long as the parent of current assignExpr node is also an assignExpr node
          // this process is different with scalar
          if ( ast.parent instanceof AssignExpr ){
              //emit( JVM.DUP );  // recursively by visitor
              ast.E2.visit(this, o);  // push the right side expr again !
          }
      }else{  // ast.E1 is varExpr
          SimpleVar var = (SimpleVar) ((VarExpr)ast.E1).V;
          Decl decl = (Decl)var.I.decl;
          
          Type type = decl.T;
          int index = decl.index;
          
          ast.E2.visit(this, o);  // already push the right side expr
          
          // need to dup as long as the parent of current assignExpr node is also an assignExpr node
          if ( ast.parent instanceof AssignExpr ){
              emit( JVM.DUP );  // recursively by visitor
              frame.push();
          }
          
          if( decl instanceof GlobalVarDecl ){
              emitPUTSTATIC( VCtoJavaType(type), var.I.spelling );
              frame.pop();
          }else{
              // store instruction for array(float/int), float, int
              storeInstForTypes( index, type, frame, false );
//            emitFSTORE(identifier); currently not use this method
          }
      }
    
      return null;
  }
  
  
  public Object visitInitExpr(InitExpr ast, Object o){
//      Frame frame = (Frame) o; 
      ast.IL.visit(this, o);
      return null;
  }
  public Object visitExprList(ExprList ast, Object o){
      Frame frame = (Frame) o;
      
//      if( ast.E instanceof IntExpr ){
//      ArrayList<Object> elementList = new ArrayList<Object>();
//      }
      int arrayIndex = 0;
      List castList = (List) ast; 
      while( !(castList instanceof EmptyExprList) ){
          ExprList list = (ExprList) castList;
//          Expr expr = list.E;
          
//          elementList.add( expr );
          emit( JVM.DUP );
          emitICONST(arrayIndex);
          frame.push(2); // because of dup and load one const?
          
          list.E.visit(this, o);
          
          if( ast.E.type.isIntType() ){
              emit( JVM.IASTORE );
          } else if( ast.E.type.isFloatType() ){
              emit( JVM.FASTORE );
          } else if( ast.E.type.isBooleanType() ){
              emit( JVM.BASTORE );
          }
          
          frame.pop( 3 );
          
          castList = list.EL;
          arrayIndex += 1;
      }
      return null;
  }

  public Object visitArrayExpr(ArrayExpr ast, Object o) {
      Frame frame = (Frame) o; 
      
      Decl referDecl = (Decl) ((SimpleVar)ast.V).I.decl;
      int localArrayIndex = referDecl.index;
      
      Type type = ((ArrayType)referDecl.T).T;
      
      if( referDecl instanceof GlobalVarDecl ){
          // It not like visitVarExpr(a), it must be array Type now(a[1])
//        if( referDecl.T.isArrayType() ){  //this global var is array type
          emitGETSTATIC( referDecl.T.toString(), ((SimpleVar)ast.V).I.spelling );
//        }else{
//            emitGETSTATIC( VCtoJavaType(referDecl.T), ((SimpleVar)ast.V).I.spelling );
//        }
      }
      else{  // local array variable
          emitLoad("ref", localArrayIndex ); // load array
      }
      frame.push();
      
      ast.E.visit(this, o);
//      int arrayIndex = Integer.parseInt( ((IntExpr)ast.E).IL.spelling );
//      frame.push();
//      emitICONST( arrayIndex );
//      frame.push(); // old version is push(2)
      
      if( type.isIntType() ) emit( JVM.IALOAD );
      else if( type.isFloatType() ) emit( JVM.FALOAD);
      else if( type.isBooleanType() ) emit( JVM.BALOAD);
      frame.pop();
      
      return null;
  }
  
  public Object visitVarExpr(VarExpr ast, Object o) {
      Frame frame = (Frame) o; 
      //if goes here, means the var must be rvalue,
      //since if it is lvalue, it will not go here, it
      //will be captured and processed in visitAssignExpr upper function
      //which should be right, only visitAssignExpr can distinguish the var
      //is the type of lvalue, and lvalue and rvalue have different instruction.
      
      Decl referDecl = (Decl) ((SimpleVar)ast.V).I.decl;
      
      if( referDecl instanceof GlobalVarDecl ){
          if( referDecl.T.isArrayType() ){  //this global var is array type
              emitGETSTATIC( referDecl.T.toString(), ((SimpleVar)ast.V).I.spelling );
          }else{
              emitGETSTATIC( VCtoJavaType(referDecl.T), ((SimpleVar)ast.V).I.spelling );
          }
          frame.push();
      }
      else{
          int localArrayIndex = referDecl.index;
          // I don't think the array type local var(not global var) will
          // go here, it will go inside visitArrayType. So just for make sure.
          if( referDecl.T.isArrayType() ){
              emitLoad("ref", localArrayIndex );   // load array
          }
          else if( referDecl.T.isFloatType() ){
              emitLoad("float", localArrayIndex ); // load float
          }
          else{
              emitLoad("int", localArrayIndex );   // load int
          }
          frame.push();
      }
      
      return null;
  }
  
  public Object visitCallExpr(CallExpr ast, Object o) {
    Frame frame = (Frame) o;
    String fname = ast.I.spelling;
    
    if (fname.equals("getInt")) {
      ast.AL.visit(this, o); // push args (if any) into the op stack
      emit("invokestatic VC/lang/System.getInt()I");
      frame.push();
    } else if (fname.equals("putInt")) {
      ast.AL.visit(this, o); // push args (if any) into the op stack
      emit("invokestatic VC/lang/System.putInt(I)V");
      frame.pop();
    } else if (fname.equals("putIntLn")) {
      ast.AL.visit(this, o); // push args (if any) into the op stack
      emit("invokestatic VC/lang/System/putIntLn(I)V");
      frame.pop();
    } else if (fname.equals("getFloat")) {
      ast.AL.visit(this, o); // push args (if any) into the op stack
      emit("invokestatic VC/lang/System/getFloat()F");
      frame.push();
    } else if (fname.equals("putFloat")) {
      ast.AL.visit(this, o); // push args (if any) into the op stack
      emit("invokestatic VC/lang/System/putFloat(F)V");
      frame.pop();
    } else if (fname.equals("putFloatLn")) {
      ast.AL.visit(this, o); // push args (if any) into the op stack
      emit("invokestatic VC/lang/System/putFloatLn(F)V");
      frame.pop();
    } else if (fname.equals("putBool")) {
      ast.AL.visit(this, o); // push args (if any) into the op stack
      emit("invokestatic VC/lang/System/putBool(Z)V");
      frame.pop();
    } else if (fname.equals("putBoolLn")) {
      ast.AL.visit(this, o); // push args (if any) into the op stack
      emit("invokestatic VC/lang/System/putBoolLn(Z)V");
      frame.pop();
    } else if (fname.equals("putString")) {
      ast.AL.visit(this, o);
      emit(JVM.INVOKESTATIC, "VC/lang/System/putString(Ljava/lang/String;)V");
      frame.pop();
    } else if (fname.equals("putStringLn")) {
      ast.AL.visit(this, o);
      emit(JVM.INVOKESTATIC, "VC/lang/System/putStringLn(Ljava/lang/String;)V");
      frame.pop();
    } else if (fname.equals("putLn")) {
      ast.AL.visit(this, o); // push args (if any) into the op stack
      emit("invokestatic VC/lang/System/putLn()V");
    } else { // programmer-defined functions

      FuncDecl fAST = (FuncDecl) ast.I.decl;

      // all functions except main are assumed to be instance methods
      if (frame.isMain()) 
        emit("aload_1"); // vc.funcname(...)
      else
        emit("aload_0"); // this.funcname(...)
      frame.push();
      
      ast.AL.visit(this, o);
      
      //no any problem, need not consider array type, since it is not allowed anyway
      String retType = VCtoJavaType(fAST.T);
      
      // The types of the parameters of the called function are not
      // directly available in the FuncDecl node but can be gathered
      // by traversing its field PL.

      int argNum = 0;
      StringBuffer argsTypes = new StringBuffer("");
      List fpl = fAST.PL;
      while (! fpl.isEmpty()) {
          if( ((ParaList) fpl).P.T.isArrayType() ){
              ArrayType arrayType = (ArrayType) ((ParaList) fpl).P.T;
              Type type = arrayType.T;
              
              if ( type.equals(StdEnvironment.booleanType) ){
                  argsTypes.append("[Z");     
              } else if ( type.equals(StdEnvironment.intType) ){
                  argsTypes.append("[I"); 
              } else{
                  argsTypes.append("[F");   
              }
          }
          else{  //scalar type
              if (((ParaList) fpl).P.T.equals(StdEnvironment.booleanType)){
                  argsTypes.append("Z");     
              } else if (((ParaList) fpl).P.T.equals(StdEnvironment.intType)){
                  argsTypes.append("I"); 
              } else{
                  argsTypes.append("F");   
              }
          }
          
          fpl = ((ParaList) fpl).PL;
          argNum += 1;
      }
      
      emit("invokevirtual", classname + "/" + fname + "(" + argsTypes + ")" + retType);
//      frame.pop(argsTypes.length() + 1);
      frame.pop(argNum + 1);
      
      if (! retType.equals("V"))
        frame.push();
    }
    return null;
  }

  public Object visitEmptyExpr(EmptyExpr ast, Object o) {
    return null;
  }

  public Object visitIntExpr(IntExpr ast, Object o) {
    ast.IL.visit(this, o);
    return null;
  }

  public Object visitFloatExpr(FloatExpr ast, Object o) {
    ast.FL.visit(this, o);
    return null;
  }

  public Object visitBooleanExpr(BooleanExpr ast, Object o) {
    ast.BL.visit(this, o);
    return null;
  }

  public Object visitStringExpr(StringExpr ast, Object o) {
    ast.SL.visit(this, o);
    return null;
  }

  // Declarations

  public Object visitDeclList(DeclList ast, Object o) {
    ast.D.visit(this, o);
    ast.DL.visit(this, o);
    return null;
  }

  public Object visitEmptyDeclList(EmptyDeclList ast, Object o) {
    return null;
  }

  public Object visitFuncDecl(FuncDecl ast, Object o) {

    Frame frame; 

    if (ast.I.spelling.equals("main")) {

       frame = new Frame(true);

      // Assume that main has one String parameter and reserve 0 for it
      frame.getNewIndex(); 

      emit(JVM.METHOD_START, "public static main([Ljava/lang/String;)V"); 
      // Assume implicitly that
      //      classname vc$; 
      // appears before all local variable declarations.
      // (1) Reserve 1 for this object reference.  // IMPORTANT !!!

      frame.getNewIndex(); 

    } else {

       frame = new Frame(false);

      // all other programmer-defined functions are treated as if
      // they were instance methods
      frame.getNewIndex(); // reserve 0 for "this"

      String retType = VCtoJavaType(ast.T);

      // The types of the parameters of the called function are not
      // directly available in the FuncDecl node but can be gathered
      // by traversing its field PL.

      StringBuffer argsTypes = new StringBuffer("");
      List fpl = ast.PL;
      while (! fpl.isEmpty()) {
          if( ((ParaList) fpl).P.T.isArrayType() ){
              ParaDecl paraDecl = ((ParaList) fpl).P;
              ArrayType arrayType = (ArrayType)paraDecl.T;
              Type type = arrayType.T;
              
              if( type.isIntType() ){
                  argsTypes.append("[I"); 
              } else if( type.isFloatType() ){
                  argsTypes.append("[F"); 
              } else if( type.isBooleanType() ){
                  argsTypes.append("[Z"); 
              }
          }
          else{
              if(((ParaList) fpl).P.T.isIntType() ){
                  argsTypes.append("I");  
              } else if( ((ParaList) fpl).P.T.isFloatType() ){
                  argsTypes.append("F");
              } else if( ((ParaList) fpl).P.T.isBooleanType() ){
                  argsTypes.append("Z");    
              }
          }
          fpl = ((ParaList) fpl).PL; 
      }

      emit(JVM.METHOD_START, ast.I.spelling + "(" + argsTypes + ")" + retType);
    }

    ast.S.visit(this, frame);

    // JVM requires an explicit return in every method. 
    // In VC, a function returning void may not contain a return, and
    // a function returning int or float is not guaranteed to contain
    // a return. Therefore, we add one at the end just to be sure.

    if (ast.T.equals(StdEnvironment.voidType)) {
      emit("");
      emit("; return may not be present in a VC function returning void"); 
      emit("; The following return inserted by the VC compiler");
      emit(JVM.RETURN); 
    } else if (ast.I.spelling.equals("main")) {
      // In case VC's main does not have a return itself
      emit(JVM.RETURN);
    } else
      emit(JVM.NOP); 

    emit("");
    emit("; set limits used by this method");
    emit(JVM.LIMIT, "locals", frame.getNewIndex());

    emit(JVM.LIMIT, "stack", frame.getMaximumStackSize());
    emit(".end method");

    return null;
  }

  public Object visitGlobalVarDecl(GlobalVarDecl ast, Object o) {
    // nothing to be done
    return null;
  }

  public Object visitLocalVarDecl(LocalVarDecl ast, Object o) {
    Frame frame = (Frame) o;
    ast.index = frame.getNewIndex();
    
    Type type = null;
    if( ast.T.isArrayType() ){
        type = ((ArrayType)ast.T).T;
        String T = VCtoJavaType(type);
        
        emit(JVM.VAR + " " + ast.index + " is " + ast.I.spelling + " [" + T + " from " + (String) frame.scopeStart.peek() + " to " +  (String) frame.scopeEnd.peek());
    }else{
        type = ast.T;
        String T = VCtoJavaType(type);
        emit(JVM.VAR + " " + ast.index + " is " + ast.I.spelling + " " + T + " from " + (String) frame.scopeStart.peek() + " to " +  (String) frame.scopeEnd.peek());
    }
    
    //array type local variable initialization
    if( ast.T.isArrayType() ){
        ArrayType arrayType = (ArrayType)ast.T;
        Type theType = arrayType.T;
        int arraySize = Integer.parseInt( ((IntExpr)arrayType.E).IL.spelling );
        
        emitICONST( arraySize );
        emit(JVM.NEWARRAY, getTypeString(theType) );
        frame.push();
        if( !ast.E.isEmptyExpr() ){
            ast.E.visit(this, frame);  // go to visitInitExpr 
        }
        emitStore( "ref", ast.index ); //array store
        frame.pop();
    }
    else{  //scalar type local variable initialization
        if( !ast.E.isEmptyExpr() ) {
            ast.E.visit(this, o);
        
            if (ast.T.equals(StdEnvironment.floatType)) {
                // cannot call emitFSTORE(ast.I) since this I is not an
                // applied occurrence 
                emitStore( "float", ast.index ); //array store
            } else {
                // cannot call emitISTORE(ast.I) since this I is not an
                // applied occurrence 
                emitStore( "int", ast.index ); //array store
            }
            frame.pop();
        }
    }
   
    return null;
  }

  // Parameters

  public Object visitParaList(ParaList ast, Object o) {
    ast.P.visit(this, o);
    ast.PL.visit(this, o);
    return null;
  }

  public Object visitParaDecl(ParaDecl ast, Object o) {
    Frame frame = (Frame) o;
    ast.index = frame.getNewIndex();
    if( ast.T.isArrayType() ){
        ArrayType arrayType = (ArrayType) ast.T;
        String T = VCtoJavaType( arrayType.T );
        emit(JVM.VAR + " " + ast.index + " is " + ast.I.spelling + " [" + T + " from " + (String) frame.scopeStart.peek() + " to " +  (String) frame.scopeEnd.peek());
    }
    else{
        String T = VCtoJavaType(ast.T);
        emit(JVM.VAR + " " + ast.index + " is " + ast.I.spelling + " " + T + " from " + (String) frame.scopeStart.peek() + " to " +  (String) frame.scopeEnd.peek());
    }
    
    return null;
  }

  public Object visitEmptyParaList(EmptyParaList ast, Object o) {
    return null;
  }

  // Arguments

  public Object visitArgList(ArgList ast, Object o) {
    ast.A.visit(this, o);
    ast.AL.visit(this, o);
    return null;
  }

  public Object visitArg(Arg ast, Object o) {
    ast.E.visit(this, o);
    return null;
  }

  public Object visitEmptyArgList(EmptyArgList ast, Object o) {
    return null;
  }

  // Types
  // ADDED
  public Object visitArrayType(ArrayType ast, Object o){
      return null;
  }
  // ADDED
  public Object visitStringType(StringType ast, Object o){
      return null;
  }

  public Object visitIntType(IntType ast, Object o) {
    return null;
  }

  public Object visitFloatType(FloatType ast, Object o) {
    return null;
  }

  public Object visitBooleanType(BooleanType ast, Object o) {
    return null;
  }

  public Object visitVoidType(VoidType ast, Object o) {
    return null;
  }

  public Object visitErrorType(ErrorType ast, Object o) {
    return null;
  }

  // Literals, Identifiers and Operators 
  public Object visitIdent(Ident ast, Object o) {
    return null;
  }

  public Object visitIntLiteral(IntLiteral ast, Object o) {
    Frame frame = (Frame) o;
    emitICONST(Integer.parseInt(ast.spelling));
    frame.push();
    return null;
  }

  public Object visitFloatLiteral(FloatLiteral ast, Object o) {
    Frame frame = (Frame) o;
    emitFCONST(Float.parseFloat(ast.spelling));
    frame.push();
    return null;
  }

  public Object visitBooleanLiteral(BooleanLiteral ast, Object o) {
    Frame frame = (Frame) o;
    emitBCONST(ast.spelling.equals("true"));
    frame.push();
    return null;
  }

  public Object visitStringLiteral(StringLiteral ast, Object o) {
    Frame frame = (Frame) o;
    emit(JVM.LDC, "\"" + ast.spelling + "\"");
    frame.push();
    return null;
  }

  public Object visitOperator(Operator ast, Object o) {
    return null;
  }

  // Variables 

  public Object visitSimpleVar(SimpleVar ast, Object o) {
    return null;
  }

  
 
  
  // Auxiliary methods for byte code generation

  // The following method appends an instruction directly into the JVM 
  // Code Store. It is called by all other overloaded emit methods.

  private void emit(String s) {
    JVM.append(new Instruction(s)); 
  }

  private void emit(String s1, String s2) {
    emit(s1 + " " + s2);
  }

  private void emit(String s1, int i) {
    emit(s1 + " " + i);
  }

  private void emit(String s1, float f) {
    emit(s1 + " " + f);
  }

  private void emit(String s1, String s2, int i) {
    emit(s1 + " " + s2 + " " + i);
  }

  private void emit(String s1, String s2, String s3) {
    emit(s1 + " " + s2 + " " + s3);
  }

  private void emitIF_ICMPCOND(String op, Frame frame) {
    String opcode;
 
    if (op.equals("i!="))   
      opcode = JVM.IF_ICMPNE;
    else if (op.equals("i=="))
      opcode = JVM.IF_ICMPEQ;
    else if (op.equals("i<"))
      opcode = JVM.IF_ICMPLT;
    else if (op.equals("i<="))
      opcode = JVM.IF_ICMPLE;
    else if (op.equals("i>"))
      opcode = JVM.IF_ICMPGT;
    else // if (op.equals("i>="))
      opcode = JVM.IF_ICMPGE;

    String falseLabel = frame.getNewLabel();
    String nextLabel = frame.getNewLabel();

    emit(opcode, falseLabel);
    frame.pop(2); 
    emit("iconst_0");
    emit("goto", nextLabel);
    emit(falseLabel + ":");
    emit(JVM.ICONST_1);
    frame.push(); 
    emit(nextLabel + ":");
  }

  private void emitFCMP(String op, Frame frame) {
    String opcode;
    
    if (op.equals("f!="))  
      opcode = JVM.IFNE;
    else if (op.equals("f=="))
      opcode = JVM.IFEQ;
    else if (op.equals("f<"))
      opcode = JVM.IFLT;
    else if (op.equals("f<="))
      opcode = JVM.IFLE;
    else if (op.equals("f>"))
      opcode = JVM.IFGT;
    else // if (op.equals("f>="))
      opcode = JVM.IFGE;

    String falseLabel = frame.getNewLabel();
    String nextLabel = frame.getNewLabel();

    emit(JVM.FCMPG);   // ??????
    
    frame.pop(2);
    
    emit(opcode, falseLabel);
    emit(JVM.ICONST_0);
    emit("goto", nextLabel);
    emit(falseLabel + ":");
    emit(JVM.ICONST_1);
    frame.push();
    emit(nextLabel + ":");
  }
  
  private void emitStore(String type, int index){
      if( type.equals("int") ){
          if (index >= 0 && index <= 3) emit( JVM.ISTORE + "_" + index ); 
          else emit( JVM.ISTORE, index ); 
      }else if( type.equals("float") ){
          if (index >= 0 && index <= 3) emit( JVM.FSTORE + "_" + index ); 
          else emit( JVM.FSTORE, index ); 
      }
      else{ // array
          if (index >= 0 && index <= 3) emit( JVM.ASTORE + "_" + index ); 
          else emit( JVM.ASTORE, index ); 
      }
  }
  private void emitLoad(String type, int index){
      if( type.equals("int") ){
          if (index >= 0 && index <= 3) emit( JVM.ILOAD + "_" + index ); 
          else emit( JVM.ILOAD, index ); 
      }else if( type.equals("float") ){
          if (index >= 0 && index <= 3) emit( JVM.FLOAD + "_" + index ); 
          else emit( JVM.FLOAD, index ); 
      }
      else{ // array
          if (index >= 0 && index <= 3) emit( JVM.ALOAD + "_" + index ); 
          else emit( JVM.ALOAD, index ); 
      }
  }

  
  private void emitGETSTATIC(String T, String I) {
    emit(JVM.GETSTATIC, classname + "/" + I, T); 
  }

  private void emitISTORE(Ident ast) {
    int index;
    if (ast.decl instanceof ParaDecl)
      index = ((ParaDecl) ast.decl).index; 
    else
      index = ((LocalVarDecl) ast.decl).index; 
    
    if (index >= 0 && index <= 3) 
      emit(JVM.ISTORE + "_" + index); 
    else
      emit(JVM.ISTORE, index); 
  }

  private void emitFSTORE(Ident ast) {
    int index;
    if (ast.decl instanceof ParaDecl)
      index = ((ParaDecl) ast.decl).index; 
    else
      index = ((LocalVarDecl) ast.decl).index; 
    if (index >= 0 && index <= 3) 
      emit(JVM.FSTORE + "_" + index); 
    else
      emit(JVM.FSTORE, index); 
  }

  private void emitPUTSTATIC(String T, String I) {
    emit(JVM.PUTSTATIC, classname + "/" + I, T); 
  }

  private void emitICONST(int value) {
    if (value == -1)
      emit(JVM.ICONST_M1); 
    else if (value >= 0 && value <= 5) 
      emit(JVM.ICONST + "_" + value); 
    else if (value >= -128 && value <= 127) 
      emit(JVM.BIPUSH, value); 
    else if (value >= -32768 && value <= 32767)
      emit(JVM.SIPUSH, value); 
    else 
      emit(JVM.LDC, value); 
  }

  private void emitFCONST(float value) {
    if(value == 0.0)
      emit(JVM.FCONST_0); 
    else if(value == 1.0)
      emit(JVM.FCONST_1); 
    else if(value == 2.0)
      emit(JVM.FCONST_2); 
    else 
      emit(JVM.LDC, value); 
  }

  private void emitBCONST(boolean value) {
    if (value)
      emit(JVM.ICONST_1);
    else
      emit(JVM.ICONST_0);
  }

  private String VCtoJavaType(Type t) {
    if (t.equals(StdEnvironment.booleanType))
      return "Z";
    else if (t.equals(StdEnvironment.intType))
      return "I";
    else if (t.equals(StdEnvironment.floatType))
      return "F";
    else // if (t.equals(StdEnvironment.voidType))
      return "V";
  }

}
