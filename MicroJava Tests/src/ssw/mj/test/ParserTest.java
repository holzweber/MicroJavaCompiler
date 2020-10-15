package ssw.mj.test;

import org.junit.Test;
import ssw.mj.Token;

import static ssw.mj.Errors.Message.*;

public class ParserTest extends CompilerTestCaseSupport {

    // ---- Working test cases

    @Test
    public void testWorkingFinalDecls() {
        init("program Test" + LF + // 1
                "  final int i = 1;" + LF + // 2
                "  final int j = 1;" + LF + // 3
                "  final int k = 1;" + LF + // 4
                "{ void main() { } }"); // 5
        parseAndVerify();
    }

    @Test
    public void testWorkingDecls() {
        init("program Test" + LF + // 1
                "  int i;" + LF + // 2
                "  int j, k;" + LF + // 3
                "{ void main() { } }"); // 4
        parseAndVerify();
    }

    @Test
    public void testWorkingMethods() {
        init("program Test" + LF + // 1
                "  int i;" + LF + // 2
                "  int j, k;" + LF + // 3
                "{" + LF + // 4
                " void foo() { }" + LF + // 5
                " void bar() { }" + LF + // 6
                " void main() { }" + LF + // 7
                " }" + LF // 8
        );
        parseAndVerify();
    }

    @Test
    public void testWorkingMethodsWithParameters() {
        init("program Test" + LF + // 1
                "{" + LF + // 2
                " void foo(int i) { }" + LF + // 3
                " void bar(int i, char c) { }" + LF + // 4
                " void main() { }" + LF + // 5
                " }" + LF // 6
        );
        parseAndVerify();
    }

    @Test
    public void testWorkingMethodsWithLocals() {
        init("program Test" + LF + // 1
                "{" + LF + // 2
                " void foo() int i; { }" + LF + // 3
                " void bar() int i; char c; { }" + LF + // 4
                " void main() { }" + LF + // 5
                " }" + LF // 6
        );
        parseAndVerify();
    }

    @Test
    public void testWorkingMethodsWithParametersAndLocals() {
        init("program Test" + LF + // 1
                "{" + LF + // 2
                " void foo(char ch) int i; { }" + LF + // 3
                " void bar(int x, int y) int i; char c; { }" + LF + // 4
                " void main() { }" + LF + // 5
                " }" + LF // 6
        );
        parseAndVerify();
    }

    @Test
    public void testWorkingMethodCall() {
        init("program Test" + LF + // 1
                "{" + LF + // 2
                " void foo(char ch) int i; { }" + LF + // 3
                " void main() { foo('a'); }" + LF + // 4
                " }" + LF // 5
        );
        parseAndVerify();
    }

    @Test
    public void testWorkingMethodCallTwoParams() {
        init("program Test" + LF + // 1
                "{" + LF + // 2
                " void foo(char ch, int x) int i; { }" + LF + // 3
                " void main() { foo('a', 1); }" + LF + // 4
                " }" + LF // 5
        );
        parseAndVerify();
    }

    @Test
    public void testWorkingMethodCallThreeParams() {
        init("program Test" + LF + // 1
                "{" + LF + // 2
                " void foo(char ch, int x, char ch2) int i; { }" + LF + // 3
                " void main() { foo('a', 1, 'b'); }" + LF + // 4
                " }" + LF // 5
        );
        parseAndVerify();
    }

    @Test
    public void testWorkingClass() {
        init("program Test" + LF + // 1
                "class X { int i; int j; }" + LF + // 2
                "{" + LF + // 3
                " void main() X x; { x = new X; }" + LF + // 4
                "}" + LF // 5
        );
        parseAndVerify();
    }

    @Test
    public void testWorkingArray() {
        init("program Test" + LF + // 1
                "{" + LF + // 2
                "  void main() int[] x; { x = new int[10]; }" + LF + // 3
                "}" + LF // 4
        );
        parseAndVerify();
    }

    @Test
    public void testWorkingIncDec() {
        init("program Test" + LF + // 1
                "{" + LF + // 2
                " void main() int i; { i--; i++; }" + LF + // 3
                " }" + LF // 4
        );
        parseAndVerify();
    }

    @Test
    public void testWorkingElseIf() {
        init("program Test {" + LF + // 1
                "  void main() int i; {" + LF + // 2
                "    if (i > 10) i++;" + LF + // 3
                "    else if (i < 5) i--;" + LF + // 4
                "    else i += 8;" + LF + // 5
                "  }" + LF + // 6
                "}");
        parseAndVerify();
    }

    @Test
    public void testWorkingLoop() {
        init("program Test {" + LF + // 1
                "  void main () int i; {" + LF + // 2
                "    i = 0;" + LF + // 3
                "    while (i < 42) {" + LF + // 4
                "	   i++;" + LF + // 5
                "    }" + LF + // 6
                "  }" + LF + // 7
                "}");
        parseAndVerify();
    }

    // ---- Errors in Parser.java

    @Test
    public void wrongStart() {
        init("noprogram Test { }");
        expectError(1, 1, TOKEN_EXPECTED, "program");
        parseAndVerify();
    }

    @Test
    public void noProgName() {
        init("program { }");
        expectError(1, 9, TOKEN_EXPECTED, "identifier");
        parseAndVerify();
    }

    @Test
    public void wrongVarDecl() {
        init("program Test " + LF + //
                "int var1,,,,var2;" + LF + //
                "{ void main() { } }");
        expectError(2, 10, TOKEN_EXPECTED, Token.Kind.ident.label());
        parseAndVerify();
    }

    @Test
    public void wrongConstDecl() {
        init("program Test" + LF + //
                "  final int i = a;" + LF + //
                "{ void main() { } }");
        expectError(2, 17, CONST_DECL);
        parseAndVerify();
    }

    @Test
    public void wrongDesignFollow() {
        init("program Test {" + LF + //
                "  void main() int i; {" + LF + //
                "    i**;" + LF + //
                "  }" + LF + //
                "}");
        expectError(3, 6, DESIGN_FOLLOW);
        parseAndVerify();
    }

    @Test
    public void wrongFactor() {
        init("program Test {" + LF + //
                "  void main () int i; {  " + LF + //
                "    i = i + if;" + LF + //
                "  }" + LF + //
                "}");
        expectError(3, 13, INVALID_FACT);
        parseAndVerify();
    }

    @Test
    public void wrongRelOp() {
        init("program Test {" + LF + //
                "  void main() int i; {" + LF + //
                "    if (i x 5);" + LF + //
                "  }" + LF + //
                "}");
        expectError(3, 11, REL_OP);
        parseAndVerify();
    }

    @Test
    public void noEof() {
        init("program Test {" + LF + //
                "  void main() {}" + LF + //
                "}moretext");
        expectError(3, 2, TOKEN_EXPECTED, "end of file");
        parseAndVerify();
    }

    @Test
    public void eof1() {
        init("program Test {" + LF + //
                "  void main() {");
        expectError(2, 15, TOKEN_EXPECTED, "}");
        parseAndVerify();
    }

    @Test
    public void eof2() {
        init("program Test {" + LF + //
                "  void main() {" + LF + //
                "    if ()");
        expectError(3, 9, INVALID_FACT);
        parseAndVerify();
    }

    @Test
    public void eof3() {
        init("program Test" + LF + //
                "  class C {" + LF + //
                "    int i");
        expectError(3, 9, TOKEN_EXPECTED, ";");
        parseAndVerify();
    }

    // ---- multiple errors & recovery
    @Test
    public void noRecover1() {
        init("program Test {" + LF + //
                "  void main this method will never recover");

        expectError(2, 13, TOKEN_EXPECTED, "(");
        parseAndVerify();
    }

    @Test
    public void noRecover2() {
        init("program Test {" + LF + //
                "  void main() {  " + LF + //
                "    if this method will never recover");

        expectError(3, 8, TOKEN_EXPECTED, "(");
        parseAndVerify();
    }

    @Test
    public void testNestedNonLabelLoops() {
        init("program Test {" + LF +
                "  void main () int i; int j; int k; {" + LF +
                "    i = 0;" + LF +
                "    while (i < 10) {" + LF +
                "	   j = 0; k = 0;" + LF +
                "      while (j < 10) {" + LF +
                "        while (k < 10) {" + LF +
                "          k++;" + LF +
                "        }" + LF +
                "	     j++;" + LF +
                "	   }" + LF +
                "	   i++;" + LF +
                "    }" + LF +
                "  }" + LF +
                "}");
        parseAndVerify();
    }

    @Test
    public void testNestedLabeledLoops() {
        init("program Test {" + LF +
                "  void main () int i; int j; int k; {" + LF +
                "    i = 0;" + LF +
                "    loop Outer: while (i < 10) {" + LF +
                "	   j = 0;" + LF +
                "      loop Middle: while (j < 10) {" + LF +
                "        k = 0;" + LF +
                "        loop Inner: while (k < 10) {" + LF +
                "          k++;" + LF +
                "        }" + LF +
                "	     j++;" + LF +
                "	   }" + LF +
                "	   i++;" + LF +
                "    }" + LF +
                "  }" + LF +
                "}");
        parseAndVerify();
    }

    @Test
    public void testNestedMixedLoops() {
        init("program Test {" + LF +
                "  void main () int i; int j; int k; {" + LF +
                "    i = 0;" + LF +
                "    loop Outer: while (i < 10) {" + LF +
                "	   j = 0;" + LF +
                "      loop Middle: while (j < 10) {" + LF +
                "        k = 0;" + LF +
                "        while (k < 10) {" + LF +
                "          k++;" + LF +
                "        }" + LF +
                "	     j++;" + LF +
                "	   }" + LF +
                "	   i++;" + LF +
                "    }" + LF +
                "  }" + LF +
                "}");
        parseAndVerify();
    }

    @Test
    public void testLabeledLoopWithoutName() {
        init("program Test {" + LF +
                "  void main () int i; {" + LF +
                "    i = 0;" + LF +
                "    loop: while (i < 10) {" + LF +
                "	   i++;" + LF +
                "    }" + LF +
                "  }" + LF +
                "}");
        expectError(4, 9, TOKEN_EXPECTED, Token.Kind.ident);
        parseAndVerify();
    }

    @Test
    public void testLabeledLoopWithoutColon() {
        init("program Test {" + LF +
                "  void main () int i; {" + LF +
                "    i = 0;" + LF +
                "    loop name while (i < 10) {" + LF +
                "	   i++;" + LF +
                "    }" + LF +
                "  }" + LF +
                "}");
        expectError(4, 15, TOKEN_EXPECTED, ":");
        parseAndVerify();
    }

    @Test
    public void testNestedLabeledLoopsWithBreak() {
        init("program Test {" + LF +
                "  void main () int i; int j; int k; {" + LF +
                "    i = 0;" + LF +
                "    loop Outer: while (i < 10) {" + LF +
                "	   j = 0;" + LF +
                "      loop Middle: while (j < 10) {" + LF +
                "        k = 0;" + LF +
                "        loop Inner: while (k < 10) {" + LF +
                "          k++;" + LF +
                "          break;" + LF +
                "        }" + LF +
                "	     j++;" + LF +
                "	   }" + LF +
                "	   i++;" + LF +
                "    }" + LF +
                "  }" + LF +
                "}");
        parseAndVerify();
    }

    @Test
    public void testNestedLabeledLoopsWithLabeledBreak() {
        init("program Test {" + LF +
                "  void main () int i; int j; int k; {" + LF +
                "    i = 0;" + LF +
                "    loop Outer: while (i < 10) {" + LF +
                "	   j = 0;" + LF +
                "      loop Middle: while (j < 10) {" + LF +
                "        k = 0;" + LF +
                "        loop Inner: while (k < 10) {" + LF +
                "          k++;" + LF +
                "          break Outer;" + LF +
                "        }" + LF +
                "	     j++;" + LF +
                "	   }" + LF +
                "	   i++;" + LF +
                "    }" + LF +
                "  }" + LF +
                "}");
        parseAndVerify();
    }
}
