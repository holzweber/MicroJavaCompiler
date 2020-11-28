package ssw.mj.impl;

import java.util.EnumSet;

import ssw.mj.Errors.Message;
import ssw.mj.Parser;
import ssw.mj.Scanner;
import ssw.mj.Token.Kind;
import ssw.mj.codegen.Code.OpCode;
import ssw.mj.codegen.Operand;
import ssw.mj.symtab.Tab;
import ssw.mj.symtab.Obj;
import ssw.mj.symtab.Struct;

import static ssw.mj.Token.Kind.*;
import static ssw.mj.Errors.Message.*;

public final class ParserImpl extends Parser {

	// heuristic errordistance
	private int errordistance = 3;
	/*
	 * define needed EnumSets, for later faster checking EnumSets are only
	 * created, if we would need more than one check per First comparison of
	 * NTS.
	 */
	private static final EnumSet<Kind> firstMethodDecl, firstStatement,
			firstRelop, firstExpr, firstAddop, firstMulop, followDecl,
			followStat, followMethodDecl;

	/**
	 * static constructor, for initializing the enumsets above
	 */
	static {
		firstMethodDecl = EnumSet.of(ident, void_);
		firstStatement = EnumSet.of(ident, if_, loop_, while_, break_, return_,
				read, print, lbrace, semicolon);
		firstRelop = EnumSet.of(eql, neq, lss, leq, gtr, geq);
		firstExpr = EnumSet.of(minus, ident, number, charConst, new_, lpar);
		firstAddop = EnumSet.of(plus, minus);
		firstMulop = EnumSet.of(times, slash, rem);

		// follows enum sets
		followDecl = EnumSet.of(lbrace, final_, class_, eof);
		followStat = EnumSet.of(rbrace, if_, loop_, while_, break_, return_,
				read, print, semicolon, eof, else_); // without lbrace and ident
		followMethodDecl = EnumSet.of(void_, eof);
	}

	/**
	 * Constructor, which sets the used Scanner and defines the EnumSet used for
	 * First - Checks
	 * 
	 * @param scanner
	 *            returns next Token of given Code
	 */
	public ParserImpl(Scanner scanner) {
		super(scanner); // initalize scanner
	}

	/**
	 * Starts the parsing.
	 */
	@Override
	public void parse() {
		scan();// scan first token of given program
		program();// first token should be NTS Program
		check(eof);// checks if src Code ended correctly
	}

	/**
	 * This method checks whether next token is the one being expected Otherwise
	 * it rises an Error, that the token is not corr.
	 * 
	 * @param expected
	 *            - Tokenkind which should be upcomming next
	 */
	private void check(Kind expected) {
		if (sym == expected) { // sym is the next token
			scan(); // the next one is correct, so we scan
		} else {
			error(TOKEN_EXPECTED, expected);
		}
	}

	/**
	 * Scans next token
	 */
	private void scan() {
		t = la; // reset the current tokens
		la = scanner.next(); // get next token(one ahead)
		sym = la.kind; // for better usage of kind
		errordistance++; // increase erordistance everytime we scan
	}

	/**
	 * Recover Method if there was an error in a Decl Step
	 * 
	 * @param errorMsg
	 *            - depends on which decl failed
	 */
	private void recoverDecl(Message errorMsg) {
		error(errorMsg);
		do {
			scan();
		} while (!(nextTokenIsType() || followDecl.contains(sym)));
	}

	/**
	 * Recover Method if there was an error in a MethDecl step
	 */
	private void recovcerMethodDecl() {
		error(METH_DECL);
		// scan until recovery point is reached
		do {
			scan();
		} while (!(nextTokenIsType() || followMethodDecl.contains(sym)));
	}

	/**
	 * if there was an error in the NTS Statement, we scan until we reached a
	 * recovery point.
	 */
	private void recoverStat() {
		error(INVALID_STAT);
		do {
			scan(); // scan until recovery point is reached
		} while (!followStat.contains(sym));
	}

	/**
	 * Adds error message to the list of errors. without starting the PanicMode
	 */
	@Override
	public void error(Message msg, Object... msgParams) {
		if (errordistance >= 3) {
			scanner.errors.error(la.line, la.col, msg, msgParams);
		}
		errordistance = 0;
	}

	/**
	 * this method is used, to check if ident is type for error handling
	 * 
	 * @return
	 */
	private boolean nextTokenIsType() {
		if (sym != ident) {
			return false;
		}
		Obj obj = tab.find(la.str);
		return obj.kind == Obj.Kind.Type;
	}

	/**
	 * This method cares about the NTS Program
	 */
	private void program() {
		check(program);
		check(ident);
		// set program name in universe
		Obj pro = tab.insert(Obj.Kind.Prog, t.str, Tab.noType);
		tab.openScope(); // opens scope after finished universe
		// used endless loop with if -else in order to dont double check
		while (sym != lbrace && sym != eof) {
			switch (sym) {
			case final_:
				constdecl();
				break;
			case ident:
				vardecl();
				break;
			case class_:
				classdecl();
				break;
			default:
				recoverDecl(INVALID_DECL);
				break;
			}
		}
		if (tab.curScope.nVars() > MAX_GLOBALS) {
			error(TOO_MANY_GLOBALS);
		}

		// set size of static data (all vars are size of 1 word)
		code.dataSize = tab.curScope.nVars();

		check(lbrace);
		// loop as long as new method starts
		while (sym != rbrace && sym != eof) {
			methoddecl();
		}
		// check if main was defined
		if (code.mainpc == -1) {
			error(METH_NOT_FOUND, "main");
		}
		if (sym != eof) {
			check(rbrace);
		}
		pro.locals = tab.curScope.locals();
		tab.closeScope(); // closes universe
	}

	/**
	 * This method cares about the NTS ConstDecl
	 */
	private void constdecl() {
		check(final_);
		StructImpl type = type();
		check(ident);
		Obj o = tab.insert(Obj.Kind.Con, t.str, type);
		check(assign);
		if (sym == number || sym == charConst) {
			// we already now, the next one is valid
			if (sym == number && type.kind != Struct.Kind.Int) {
				// we dont got integer, but assign number
				recoverDecl(CONST_TYPE);
			} else if (sym == charConst && type.kind != Struct.Kind.Char) {
				// we dont got char, but assign charConst
				recoverDecl(CONST_TYPE);
			} else {
				scan();
				o.val = t.val; // set value of cosntant
				check(semicolon);
			}
		} else {
			recoverDecl(CONST_DECL); // const decl is not used corr.
		}
	}

	/**
	 * This method cares about the NTS VarDecl
	 */
	private void vardecl() {
		// get type, if not defined type() will throw error
		StructImpl type = type();
		// check if we have a valid type
		// for a variable decl.
		if (type.kind == Struct.Kind.None) {
			recoverDecl(NO_TYPE);
		}
		check(ident);
		tab.insert(Obj.Kind.Var, t.str, type);
		while (sym == comma) {
			scan();// we know commma will happen
			check(ident);
			tab.insert(Obj.Kind.Var, t.str, type);
		}
		check(semicolon);
	}

	/**
	 * This method cares about the NTS ClassDecl
	 */
	private void classdecl() {
		check(class_);
		check(ident);
		Obj clazz = tab.insert(Obj.Kind.Type, t.str,
				new StructImpl(Struct.Kind.Class));
		check(lbrace);
		tab.openScope();
		while (sym == ident) {// ident is First(VarDecl)
			vardecl();// so vardecl has to follow since lang. is LL1 comp.
		}
		if (tab.curScope.nVars() > MAX_FIELDS) {
			error(TOO_MANY_FIELDS);
		}
		clazz.type.fields = tab.curScope.locals();
		tab.closeScope();
		check(rbrace);
	}

	/**
	 * This method cares about the NTS MethodDecl Special check for main methods
	 * : should be void AND not have parameters
	 */
	private void methoddecl() {

		if (!firstMethodDecl.contains(sym)) { // wrong start of method detected
			recovcerMethodDecl();
			return;// break current methodDecl
		}
		StructImpl type = Tab.noType;
		Kind returnType = sym; // store return Type of method
		if (sym == ident) {
			if (!nextTokenIsType()) { // method has not a corr. type
				recovcerMethodDecl();
				return;// break current methodDecl
			}
			type = type(); // type of method changed!
		} else if (sym == void_) {
			scan();
		}

		check(ident);
		Obj meth = tab.insert(Obj.Kind.Meth, t.str, type);
		check(lpar); // method block starts
		boolean tempFormPars = false; // check for formPars (important if main)
		tab.openScope(); // open new scope of method
		// optional form pars
		if (sym == ident) {
			formpars();
			tempFormPars = true; // we defined formpars!
		}
		check(rpar);// check if methodhead is ended correctly
		meth.nPars = tab.curScope.nVars(); // save curr number of formpars
		// we need to check the main method
		if ("main".equals(meth.name)) {
			code.mainpc = code.pc; // set main programm counter
			if (tempFormPars) { // main is not allowed to have formpars!
				error(MAIN_WITH_PARAMS);
			}
			if (returnType != void_) { // check if returnType is void
				error(MAIN_NOT_VOID);
			}

		}

		while (sym == ident) {
			vardecl();
		}
		// check if too many local elements were defined
		if (tab.curScope.nVars() > MAX_LOCALS) {
			error(TOO_MANY_LOCALS);
		}
		meth.locals = tab.curScope.locals(); // relink locals from created scope
		// Code generation for entering a method,
		// defining how many parameters and locals we have
		code.put(OpCode.enter);
		code.put(meth.nPars);
		code.put(tab.curScope.nVars());
		block();
		if (meth.type == Tab.noType) {
			code.put(OpCode.exit);
			code.put(OpCode.return_);
		} else { // end of function reached without a return statement
			code.put(OpCode.trap);
			code.put(1);
		}
		code.put(OpCode.exit);
		code.put(OpCode.return_);
		tab.closeScope(); // close method scope
	}

	/**
	 * This method cares about the NTS FormPars
	 */
	private void formpars() {
		StructImpl type = type();
		check(ident);
		tab.insert(Obj.Kind.Var, t.str, type);
		while (sym == comma) {
			scan();// we know commma happened
			type = type();
			check(ident);
			tab.insert(Obj.Kind.Var, t.str, type);
		}
	}

	/**
	 * This method cares about the NTS Type
	 * 
	 * @return
	 */
	private StructImpl type() {
		check(ident);
		Obj o = tab.find(t.str);
		if (o.kind != Obj.Kind.Type) {
			error(NO_TYPE);
		}
		StructImpl type = o.type;
		if (sym == lbrack) {
			scan();// we know lbrack happened
			check(rbrack);
			type = new StructImpl(type);
		}
		return type;
	}

	/**
	 * This method cares about the NTS Block
	 */
	private void block() {
		check(lbrace);
		if (t.kind == lbrace) { // in case of error
			while (sym != eof && sym != rbrace) {
				statement();
			}
		}
		check(rbrace);
	}

	/**
	 * This method cares about the NTS Statement
	 */
	private void statement() {
		if (!firstStatement.contains(sym)) {
			recoverStat();
		}
		switch (sym) {
		case ident:// Designator
			Operand op = designator();
			Operand op2;
			switch (sym) {
			case assign:
				scan(); // dont need to remeber assignop
				op2 = expr();
				// check if designator object is a Variable
				if (op.obj != null && op.obj.kind != Obj.Kind.Var) {
					error(NO_VAR);
				}
				// check if expr is assignable to the desig
				if (op2.type.assignableTo(op.type)) {
					code.assign(op, op2);
				} else {
					error(INCOMP_TYPES);
				}
				break;
			case plusas:
			case minusas:
			case timesas:
			case slashas:
			case remas:
				OpCode calc = assignop(); // store which calc should be made
				// check if designator object is a Variable
				if (op.obj != null && op.obj.kind != Obj.Kind.Var) {
					error(NO_VAR);
				}
				// Code generation
				if (op.kind == Operand.Kind.Fld) {
					code.put(OpCode.dup);
				} else if (op.kind == Operand.Kind.Elem) {
					code.put(OpCode.dup2);
				}
				Operand.Kind tempKind = op.kind;
				code.load(op);
				op.kind = tempKind;

				op2 = expr(); // get expr

				// check compatility
				if (op.type != Tab.intType || op2.type != Tab.intType) {
					error(NO_INT_OP);
				}
				if (!op2.type.assignableTo(op.type)) {
					error(INCOMP_TYPES);
				}
				code.load(op2);
				code.put(calc);
				code.assign(op, null);
				break;
			case lpar:
				actpars();
				break;
			case pplus:
				// Do Error Checking
				if (op.type != Tab.intType) {
					error(NO_INT);
				}
				if (op.obj != null && op.obj.kind != Obj.Kind.Var) {
					error(NO_VAR);
				}
				scan(); // scans pplus
				// Now duplicate if needed
				if (op.kind == Operand.Kind.Fld) {
					code.put(OpCode.dup);
				} else if (op.kind == Operand.Kind.Elem) {
					code.put(OpCode.dup2);
				}

				if (op.kind == Operand.Kind.Local) {
					code.put(OpCode.inc);
					code.put(op.adr);
					code.put(1);
				} else {
					tempKind = op.kind;// we need to remember the type
					code.load(op);// type is now stack
					op.kind = tempKind; // reset type
					code.load(new Operand(1));
					code.put(OpCode.add);
					code.assign(op, null);
				}
				break;
			case mminus:
				// Do Error Checking
				if (op.type != Tab.intType) {
					error(NO_INT);
				}
				if (op.obj != null && op.obj.kind != Obj.Kind.Var) {
					error(NO_VAR);
				}
				scan(); // scans mminus
				// Now duplicate if needed
				if (op.kind == Operand.Kind.Fld) {
					code.put(OpCode.dup);
				} else if (op.kind == Operand.Kind.Elem) {
					code.put(OpCode.dup2);
				}

				if (op.kind == Operand.Kind.Local) {
					code.put(OpCode.inc);
					code.put(op.adr);
					code.put(255);
				} else {
					tempKind = op.kind; // we need to remember the type
					code.load(op); // type is now stack
					op.kind = tempKind; // reset type
					code.load(new Operand(-1));
					code.put(OpCode.add);
					code.assign(op, null);
				}
				break;
			default:
				error(DESIGN_FOLLOW);
			}
			check(semicolon);
			break;
		case if_:
			scan();
			check(lpar);
			condition();
			check(rpar);
			statement();
			if (sym == else_) {
				scan();
				statement();
			}
			break;
		case loop_: // renewedloop
			scan();// first token after loop
			tab.openScope();
			check(ident);
			tab.insert(Obj.Kind.Label, t.str, Tab.noType);
			check(colon);
			check(while_);
			check(lpar);
			condition();
			check(rpar);
			statement();
			tab.closeScope();
			break;
		case while_:
			scan(); // check because we wanna use loop here too
			check(lpar);
			condition();
			check(rpar);
			statement();
			break;
		case break_:
			scan();
			if (sym == ident) {
				scan();
				// check if ident is defined label
				if (tab.find(t.str).kind != Obj.Kind.Label) {
					error(NO_LABEL);
				}
			}
			check(semicolon);
			break;
		case return_:
			scan();
			if (firstExpr.contains(sym)) {
				Operand x = expr();
				if (true) // check assignable
					code.load(x);
			} else {
				// check if void
			}
			code.put(OpCode.exit);
			code.put(OpCode.return_);
			check(semicolon);
			break;
		case read:
			scan();
			check(lpar);
			op = designator();
			if (op.type == Tab.intType) {
				code.put(OpCode.read);
				code.assign(op, new Operand(Tab.intType));
			} else if (op.type == Tab.charType) {
				code.put(OpCode.bread);
				code.assign(op, new Operand(Tab.charType));
			} else {
				error(READ_VALUE);
			}
			check(rpar);
			check(semicolon);
			break;
		case print:
			scan();
			check(lpar);
			op = expr();
			// standardwidth for printing
			int width = 0; // after comma there dont have to be a given width!
			if (sym == comma) {
				scan();// we know comma happened here
				check(number);
				width = t.val; // coder put a number in here, so reset width
			}
			code.load(op);
			code.load(new Operand(width));
			if (op.type.kind == Struct.Kind.Int) {
				code.put(OpCode.print);
			} else if (op.type.kind == Struct.Kind.Char) {
				code.put(OpCode.bprint);
			} else {
				error(PRINT_VALUE);
			}
			check(rpar);
			check(semicolon);
			break;
		case lbrace:
			block();
			break;
		case semicolon:
			scan();
			break;
		default:// if none of above worked, we got an error here
			error(INVALID_STAT);
		}
	}

	/**
	 * This method cares about the NTS AssignOp Return return sthe used assign
	 * operation as OpCode for code generation
	 */
	private OpCode assignop() {
		switch (sym) {
		case assign:
			scan();
			return OpCode.nop;
		case plusas:
			scan();
			return OpCode.add;
		case minusas:
			scan();
			return OpCode.sub;
		case timesas:
			scan();
			return OpCode.mul;
		case slashas:
			scan();
			return OpCode.div;
		case remas:
			scan();
			return OpCode.rem;
		default:
			error(ASSIGN_OP);
			return OpCode.nop;
		}
	}

	/**
	 * This method cares about the NTS ActPars
	 */
	private void actpars() {
		check(lpar);
		if (firstExpr.contains(sym)) {// check if expr follows
			expr();
			while (sym == comma) {
				scan();// we know commma happened
				expr();
			}
		}
		check(rpar);
	}

	/**
	 * This method cares about the NTS Condition
	 */
	private void condition() {
		condterm();
		while (sym == or) {
			scan();// we know 'or' happened
			condterm();
		}
	}

	/**
	 * This method cares about the NTS CondTerm
	 */
	private void condterm() {
		condfact();
		while (sym == and) {
			scan();// we know 'and' happened
			condfact();
		}
	}

	/**
	 * This method cares about the NTS CondFact
	 */
	private void condfact() {
		expr();
		relop();
		expr();
	}

	/**
	 * This method cares about the NTS Relop
	 */
	private void relop() {
		if (firstRelop.contains(sym)) {
			scan();
		} else {
			error(REL_OP);
		}
	}

	/**
	 * This method cares about the NTS Expr
	 */
	private Operand expr() {
		// for compiler optimization, remember if we can negate operand of term
		boolean neg = false;
		if (sym == minus) {
			scan();// we know minus happened
			neg = true;
		}
		Operand op = term();
		if (neg) {
			if (op.type != Tab.intType) {
				error(NO_INT_OP);
			}
			if (op.kind == Operand.Kind.Con) {
				op.val = -op.val; // optimize!
			} else {
				code.load(op);
				code.put(OpCode.neg);
			}
		}

		while (firstAddop.contains(sym)) {
			OpCode calc = addop();
			code.load(op);
			Operand op2 = term();
			code.load(op2);
			code.put(calc);
			if (op.type != Tab.intType || op2.type != Tab.intType) {
				error(NO_INT_OP);
			}
		}
		return op;
	}

	/**
	 * This method cares about the NTS Term
	 */
	private Operand term() {
		Operand op = factor();
		while (firstMulop.contains(sym)) {
			OpCode calc = mulop();
			code.load(op); // we know we onna mulop something
			Operand op2 = factor();
			if (op.type != Tab.intType || op2.type != Tab.intType) {
				error(NO_INT_OP);
			}
			code.load(op2);
			code.put(calc);
		}
		return op;
	}

	/**
	 * This method cares about the NTS Factor
	 */
	private Operand factor() {
		Operand op;
		switch (sym) {
		case ident:
			op = designator();
			if (sym == lpar) {
				if (op.kind != Operand.Kind.Meth) {
					error(NO_METH); // has to be method!
				}
				if (op.obj.type == Tab.noType) {
					error(INVALID_CALL); // we expected a return value !
				}
				actpars();
				// value should be on stack after meth call on stack
				op.kind = Operand.Kind.Stack;
			}
			break;
		case number:
			scan();
			op = new Operand(t.val);
			break;
		case charConst:
			scan();
			op = new Operand(t.val);
			op.type = Tab.charType; // because constructor sets it to int
			break;
		case new_:
			scan();
			check(ident);
			Obj obj = tab.find(t.str); // check if class or type exists
			if (obj.kind != Obj.Kind.Type) {
				error(NO_TYPE);
			}
			StructImpl type = obj.type;
			if (sym == lbrack) {
				scan();
				op = expr();
				// expr has to be type int
				if (op.type != Tab.intType) {
					error(ARRAY_SIZE);
				}
				code.load(op);
				code.put(OpCode.newarray);
				if (type == Tab.charType) {
					code.put(0);
				} else {
					code.put(1);
				}

				type = new StructImpl(type);
				check(rbrack);
			} else {
				if (obj.kind != Obj.Kind.Type
						|| type.kind != Struct.Kind.Class) {
					error(NO_CLASS_TYPE);
				} else {
					code.put(OpCode.new_);
					code.put2(type.nrFields());
				}
			}
			op = new Operand(type);
			break;
		case lpar:
			scan();
			op = expr();
			check(rpar);
			break;
		default:
			error(INVALID_FACT);
			op = new Operand(1); // factor 1 cant destroy something
			break;
		}
		return op;
	}

	/**
	 * This method cares about the NTS Designator
	 */
	private Operand designator() {
		check(ident);
		Operand op = new Operand(tab.find(t.str), this);
		for (;;) {
			if (sym == period) {
				if (op.type.kind != Struct.Kind.Class) {
					error(NO_CLASS);
				}
				scan();// we know period will happen
				code.load(op);
				check(ident);
				// checks if ident is field of class
				Obj obj = tab.findField(t.str, op.type);
				op.kind = Operand.Kind.Fld;
				op.type = obj.type;
				op.adr = obj.adr;
			} else if (sym == lbrack) {
				if (op.obj != null && op.obj.kind != Obj.Kind.Var) {
					error(NO_VAL);
				}
				scan();// we know lbrack has happened
				code.load(op);
				Operand op2 = expr();
				if (op.type.kind != Struct.Kind.Arr) {
					error(NO_ARRAY);
				}
				if (op2.type != Tab.intType) {
					error(ARRAY_INDEX);
				}
				code.load(op2);
				op.kind = Operand.Kind.Elem;
				op.type = op.type.elemType;
				check(rbrack);
			} else {
				break; // break from loop
			}
		}
		return op;
	}

	/**
	 * This method cares about the NTS AddOp
	 */
	private OpCode addop() {
		switch (sym) {
		case plus:
			scan();
			return OpCode.add;
		case minus:
			scan();
			return OpCode.sub;
		default:
			error(ADD_OP); // we do a No operation
			return OpCode.nop;
		}
	}

	/**
	 * This method cares about the NTS MulOp returns the needed OpCode for
	 * Codegeneration
	 */
	private OpCode mulop() {
		switch (sym) {
		case times:
			scan();
			return OpCode.mul;
		case slash:
			scan();
			return OpCode.div;
		case rem:
			scan();
			return OpCode.rem;
		default:
			error(MUL_OP);
			return OpCode.nop;
		}
	}
}
