/*
 * JVM.java
 */

package VC.CodeGen;

import java.io.*;

// This JVM includes only a subset the JVM instructions

public final class JVM {

// CODE STORE

  public static int nextInstAddr = 0;
  public static int codeSize = 512;
  public static Instruction[] code = new Instruction[codeSize];

  // In production compilers, expensive array copies will be avoided
  // when dynamic data structures such as linked lists are used.
  public static void append(Instruction inst) {
    if (nextInstAddr >= codeSize) {
      Instruction[] newCode = new Instruction[2 * codeSize];
      System.arraycopy(code, 0, newCode, 0, codeSize); 
      codeSize = 2 * code.length;
      code = newCode;
    }
    
    code[nextInstAddr++] = inst;
  }
  
  public static void dump(String filename) {
    PrintWriter writer;
    try {
      writer = new PrintWriter(new FileOutputStream(filename));
      for (int addr = 0; addr < nextInstAddr; addr++)
      JVM.code[addr].write(writer);

      writer.close();
    } catch (FileNotFoundException e) {
      System.out.println ("Error opening object file: " + e); 
      System.exit(1);
    } catch (Exception e) {
      System.out.println ("Error writing object file: " + e); 
      System.exit(1);
    }
  }

// Jasmin ASSEMBLY INSTRUCTIONS

  // Directive codes
  public final static String
    SOURCE = ".source",
    CLASS = ".class",
    STATIC_FIELD  = ".field static",
    LIMIT = ".limit",
    METHOD_START = ".method",
    METHOD_END = ".end",
    SUPER = ".super",
    VAR  = ".var",
    LINE  = ".line",

  // JVM operation codes
    
    
  // array-related instruction
     IALOAD = "iaload",  // load from an int array
     FALOAD = "faload",  // load from a float array
     BALOAD = "baload",  // load from a boolean array
     
     IASTORE = "iastore", // store into an int array
     FASTORE = "fastore", // store into a float array
     BASTORE = "bastore", // store into a boolean array
     
     NEWARRAY = "newarray", // new array instance
     

  // Arithmetic Instructions

     FADD = "fadd",   //finish
     IADD = "iadd",   //finish
     FSUB = "fsub",   //finish
     ISUB = "isub",   //finish
     FMUL = "fmul",   //finish
     IMUL = "imul",   //finish
     FDIV = "fdiv",   //finish
     IDIV = "idiv",   //finish
     FCMPG = "fcmpg", //finish
     FCMPL = "fcmpl",
     FNEG = "fneg",
     INEG = "ineg",
     IXOR = "ixor",
     WIDE = "wide", // not supported by Jasmin


  // Loading and storing instructions
    GETSTATIC = "getstatic", //finish
    PUTSTATIC = "putstatic", //finish

    // load a local variable into the operand stack
    ALOAD = "aload",
    ALOAD_0 = "aload_0",
    ALOAD_1 = "aload_1",
    ALOAD_2 = "aload_2",
    ALOAD_3 = "aload_3",
    ILOAD = "iload",
    ILOAD_0 = "iload_0",
    ILOAD_1 = "iload_1",
    ILOAD_2 = "iload_2",
    ILOAD_3 = "iload_3",
    FLOAD = "fload",
    FLOAD_0 = "fload_0",
    FLOAD_1 = "fload_1",
    FLOAD_2 = "fload_2",
    FLOAD_3 = "fload_3",
    // store the operand stack into a local variable
    ASTORE = "astore",
    ASTORE_0 = "astore_0",
    ASTORE_1 = "astore_1",
    ASTORE_2 = "astore_2",
    ASTORE_3 = "astore_3",
    FSTORE = "fstore",
    FSTORE_0 = "fstore_0",
    FSTORE_1 = "fstore_1",
    FSTORE_2 = "fstore_2",
    FSTORE_3 = "fstore_3",
    ISTORE = "istore",
    ISTORE_0 = "istore_0",
    ISTORE_1 = "istore_1",
    ISTORE_2 = "istore_2",
    ISTORE_3 = "istore_3",
    // load a constant into the operand stack

    ICONST = "iconst",     // does not exist; used in the method
			   // emitICONST of the class Emitter.

    ICONST_M1 = "iconst_m1",
    ICONST_0 = "iconst_0",
    ICONST_1 = "iconst_1",
    ICONST_2 = "iconst_2",
    ICONST_3 = "iconst_3",
    ICONST_4 = "iconst_4",
    ICONST_5 = "iconst_5",
    FCONST_0 = "fconst_0",
    FCONST_1 = "fconst_1",
    FCONST_2 = "fconst_2",
    BIPUSH = "bipush",
    SIPUSH = "sipush",
    LDC = "ldc",

  // Method invocation and return instructions

    INVOKESTATIC = "invokestatic",     //finish
    INVOKESPECIAL = "invokespecial",   //finish
    INVOKEVIRTUAL = "invokevirtual",
    FRETURN = "freturn",
    IRETURN = "ireturn",
    RETURN = "return",                 //finish

  // Control transfer instructions

    GOTO = "goto",  //finish
    IFEQ = "ifeq",  //finish
    IFNE = "ifne",  //finish
    IFLE = "ifle",  //finish
    IFLT = "iflt",  //finish
    IFGE = "ifge",  //finish
    IFGT = "ifgt",  //finish
    IF_ICMPEQ = "if_icmpeq", //finish
    IF_ICMPNE = "if_icmpne", //finish
    IF_ICMPLE = "if_icmple", //finish
    IF_ICMPLT = "if_icmplt", //finish
    IF_ICMPGE = "if_icmpge", //finish
    IF_ICMPGT = "if_icmpgt", //finish

  // Type conversion instructions
    I2F = "i2f",

  // Object creation and manipulation
    NEW = "new", //finish

  // Operand Stack management instructions
   
    DUP = "dup", //finish
    POP = "pop", 
    NOP = "nop"; //finish

// Limitations of the JVM 

  public final static int
    MAX_BYTE = 255,  // 2^8 -1
    MAX_SHORT = 65535, // 2^16 - 1
    MAX_LOCALVARINDEX = MAX_SHORT, 
    MAX_OPSTACK = MAX_SHORT;
}
