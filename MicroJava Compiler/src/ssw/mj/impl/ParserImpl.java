package ssw.mj.impl;

import ssw.mj.Errors.Message;
import ssw.mj.Parser;
import ssw.mj.Scanner;
import ssw.mj.Token.Kind;
import ssw.mj.codegen.Code;
import ssw.mj.codegen.Code.OpCode;
import ssw.mj.codegen.Label;
import ssw.mj.codegen.Operand;
import ssw.mj.symtab.Tab;
import ssw.mj.symtab.Obj;
import ssw.mj.symtab.Struct;

import static ssw.mj.Token.Kind.*;

import java.util.*;

import static ssw.mj.Errors.Message.*;

public final class ParserImpl extends Parser {

	// heuristic errordistance
	private int errordistance = 3;
	private static final int MIN_ERROR_DIST = 3; // standard minimal error dist
	private static final int DEFAULT_WIDTH = 1; // standard width for print
												// statement
	private Obj curMethod;
	/*
	 * define needed EnumSets, for later faster checking EnumSets are only
	 * created, if we would need more than one check per First comparison of
	 * NTS.
	 */
	private static final EnumSet<Kind> firstMethodDecl, firstStatement,
			firstExpr, firstAddop, firstMulop, followDecl, followStat,
			followMethodDecl;

	/**
	 * static constructor, for initializing the enumsets above
	 */
	static {
		firstMethodDecl = EnumSet.of(ident, void_);
		firstStatement = EnumSet.of(ident, if_, loop_, while_, break_, return_,
				read, print, lbrace, semicolon);
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
		if (errordistance >= MIN_ERROR_DIST) {
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
		curMethod = tab.insert(Obj.Kind.Meth, t.str, type);
		check(lpar); // method block starts
		boolean tempFormPars = false; // check for formPars (important if main)
		tab.openScope(); // open new scope of method
		// optional form pars
		if (sym == ident) {
			formpars();
			tempFormPars = true; // we defined formpars!
		}
		check(rpar);// check if methodhead is ended correctly
		curMethod.nPars = tab.curScope.nVars(); // save curr number of formpars
		// we need to check the main method
		if ("main".equals(curMethod.name)) {
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
		curMethod.locals = tab.curScope.locals(); // relink locals from created
													// scope
		// Code generation for entering a method,
		// defining how many parameters and locals we have
		curMethod.adr = code.pc;
		code.put(OpCode.enter);
		code.put(curMethod.nPars);
		code.put(tab.curScope.nVars());
		block(null);
		// taken from lecture slides
		if (curMethod.type == Tab.noType) {
			code.put(OpCode.exit);
			code.put(OpCode.return_);
		} else { // end of function reached without a return statement
			code.put(OpCode.trap);
			code.put(1);
		}
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
	private void block(Label breakLabel) {
		check(lbrace);
		if (t.kind == lbrace) { // in case of error
			while (sym != eof && sym != rbrace) {
				statement(breakLabel);
			}
		}
		check(rbrace);
	}

	/**
	 * This method cares about the NTS Statement
	 */
	private void statement(Label breakLabel) {
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
				// duplicate
				code.duplicate(op);

				Operand.Kind tempKind = op.kind; // store kind
				code.load(op); // load operand, - kind is now stack
				op.kind = tempKind; // reset kind

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
				code.store(op);
				break;
			case lpar:
				actpars(op);
				code.put(Code.OpCode.call);
				code.put2(op.adr - (code.pc - 1));
				if (op.type != Tab.noType) {
					code.put(Code.OpCode.pop);
				}
				break;
			case pplus:
			case mminus:
				// Do Error Checking
				if (op.type != Tab.intType) {
					error(NO_INT);
				}
				if (op.obj != null && op.obj.kind != Obj.Kind.Var) {
					error(NO_VAR);
				}
				// duplicate
				code.duplicate(op);
				// increment
				if (sym == pplus) {
					code.inc(op, 1);
				} else {
					code.inc(op, -1);
				}
				scan(); // scans mminus or pplus
				break;
			default:
				error(DESIGN_FOLLOW);
			}
			check(semicolon);
			break;
		case if_:
			Label end;
			scan();
			check(lpar);
			op = condition();
			code.fJump(op); // if condition if flase prepare jump
			op.tLabel.here();
			check(rpar);
			statement(breakLabel);
			if (sym == else_) {
				end = new LabelImpl(code); // need sep. label because of else
				code.jump(end);
				op.fLabel.here();
				scan();
				statement(breakLabel);
				end.here();
			} else {
				op.fLabel.here();
			}

			break;
		case loop_: // renewedloop
			scan();// first token after loop
			tab.openScope();
			check(ident);
			// insert Label into SymTab
			Obj obj = tab.insert(Obj.Kind.Label, t.str, Tab.noType);
			check(colon);
			check(while_);
			check(lpar);
			Label top = new LabelImpl(code);
			top.here();
			op = condition();
			// we wanna break out of loop, so we need false label of cond
			obj.label = op.fLabel; // we can reuse the label
			breakLabel = obj.label;// put label of current loop onto stack
			code.fJump(op);
			op.tLabel.here();
			check(rpar);
			statement(breakLabel);
			code.jump(top); // jump back to loop top
			op.fLabel.here(); // condition of loo
			tab.closeScope();
			break;
		case while_:
			scan(); // check because we wanna use loop here too
			check(lpar);
			top = new LabelImpl(code);
			top.here();
			op = condition();
			code.fJump(op);
			// use false label to break out of while loop
			breakLabel = op.fLabel;
			op.tLabel.here();
			check(rpar);
			statement(breakLabel);
			code.jump(top);
			op.fLabel.here();
			break;
		case break_:
			scan();
			if (sym == ident) {
				scan();
				obj = tab.find(t.str);
				// check if ident is defined label
				if (obj.kind != Obj.Kind.Label) {
					error(NO_LABEL);
				}
				if (obj.label == null) { // no label defined
					error(NOT_FOUND, t.str);
				} else {
					code.jump(obj.label);
				}
			} else { // unlabeld break
				if (breakLabel == null) {
					error(NO_LOOP);
				} else {
					code.jump(breakLabel);
				}
			}
			check(semicolon);
			break;
		case return_:
			scan();
			if (firstExpr.contains(sym)) {
				if (curMethod.type == Tab.noType) {
					error(RETURN_VOID);
				}
				op = expr();
				code.load(op);
				if (!op.type.assignableTo(curMethod.type)) {
					error(RETURN_TYPE);
				}
			} else if (curMethod.type != Tab.noType) {
				error(RETURN_NO_VAL);
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
				code.store(op);
			} else if (op.type == Tab.charType) {
				code.put(OpCode.bread);
				code.store(op);
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
			int width = DEFAULT_WIDTH; // after comma there dont have to be a
										// given width!
			if (sym == comma) {
				scan();// we know comma happened here
				check(number);
				width = t.val; // coder put a number in here, so reset width
			}
			code.load(op);
			code.load(new Operand(width));
			if (op.type.kind == Struct.Kind.Int) {
				code.put(OpCode.print); // print for Integer Kind
			} else if (op.type.kind == Struct.Kind.Char) {
				code.put(OpCode.bprint); // brint used if i only need to print a
											// byte
			} else {
				error(PRINT_VALUE);
			}
			check(rpar);
			check(semicolon);
			break;
		case lbrace:
			block(breakLabel);
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
			return OpCode.nop; // No operation should be done
		}
	}

	/**
	 * This method cares about the NTS ActPars
	 * 
	 * @param op
	 */
	private void actpars(Operand m) {
		check(lpar);
		Operand ap;
		if (m.kind != Operand.Kind.Meth) {
			error(NO_METH);
			m.obj = tab.noObj;
		}
		int aPars = 0;
		int fPars = m.obj.nPars;
		Iterator<Obj> fp = m.obj.locals.values().iterator();
		if (firstExpr.contains(sym)) {// check if expr follows
			ap = expr();
			code.load(ap);
			aPars++;
			if (fp.hasNext()) {
				Obj o = fp.next();
				if (!ap.type.assignableTo(o.type)) {
					error(PARAM_TYPE);
				}
			}
			while (sym == comma) {
				scan();// we know commma happened
				ap = expr();
				code.load(ap);
				aPars++;
				if (fp.hasNext()) {
					Obj o = fp.next();
					if (!ap.type.assignableTo(o.type)) {
						error(PARAM_TYPE);
					}
				}
			}
			if (aPars > fPars) {
				error(MORE_ACTUAL_PARAMS);
			} else if (aPars < fPars) {
				error(LESS_ACTUAL_PARAMS);
			}
		}
		check(rpar);
	}

	/**
	 * This method cares about the NTS Condition
	 */
	private Operand condition() {
		Operand op = condterm();
		while (sym == or) {
			code.tJump(op);
			scan();// we know 'or' happened
			op.fLabel.here();
			Operand op2 = condterm();
			op.fLabel = op2.fLabel;
			op.op = op2.op;
		}
		return op;
	}

	/**
	 * This method cares about the NTS CondTerm
	 */
	private Operand condterm() {
		Operand op = condfact();
		while (sym == and) {
			code.fJump(op);
			scan();
			Operand op2 = condfact();
			op.op = op2.op;
		}
		return op;
	}

	/**
	 * This method cares about the NTS CondFact
	 */
	private Operand condfact() {
		Operand op = expr();
		code.load(op);
		Code.CompOp comp = relop();
		Operand op2 = expr();
		code.load(op2);
		if (!op.type.compatibleWith(op2.type)) {
			error(INCOMP_TYPES);
		}
		if (op.type.isRefType() && comp != Code.CompOp.eq
				&& comp != Code.CompOp.ne) {
			error(EQ_CHECK);
		}

		return new Operand(comp, code);
	}

	/**
	 * This method cares about the NTS Relop
	 */
	private Code.CompOp relop() {
		switch (sym) {
		case eql:
			scan();
			return Code.CompOp.eq;
		case neq:
			scan();
			return Code.CompOp.ne;
		case lss:
			scan();
			return Code.CompOp.lt;
		case leq:
			scan();
			return Code.CompOp.le;
		case gtr:
			scan();
			return Code.CompOp.gt;
		case geq:
			scan();
			return Code.CompOp.ge;
		default:
			error(REL_OP);
			return Code.CompOp.eq; // better then returning null
		}
	}

	/**
	 * This method cares about the NTS Expr
	 */
	private Operand expr() {
		// for compiler optimization, remember if we can negate operand of term
		boolean neg = false; // flag which checks, if we can optimize
		if (sym == minus) {
			scan();// we know minus happened
			neg = true; // minus before first term!
		}
		Operand op = term();
		if (neg) {
			if (op.type != Tab.intType) {
				error(NO_INT_OP); // can only negate integer
			}
			if (op.kind == Operand.Kind.Con) { // constant optimizsation
				op.val = -op.val; // just change value
			} else {
				code.load(op); // otherwise do it the long way
				code.put(OpCode.neg);
			}
		}

		while (firstAddop.contains(sym)) {
			OpCode calc = addop();
			code.load(op); // load operand for add operation
			Operand op2 = term();
			if (op.type != Tab.intType || op2.type != Tab.intType) {
				error(NO_INT_OP); // can only do a addop if both are intType
			}
			code.load(op2); // load second term
			code.put(calc);
		}
		return op;
	}

	/**
	 * This method cares about the NTS Term return a created new Operand
	 */
	private Operand term() {
		Operand op = factor(); // get Operand from Factor
		while (firstMulop.contains(sym)) {
			OpCode calc = mulop();
			// we know we onna mulop something - need to load operand
			code.load(op);
			Operand op2 = factor(); // get second factor of mul operation
			if (op.type != Tab.intType || op2.type != Tab.intType) {
				error(NO_INT_OP); // can only do a mulop if both are intType
			}
			code.load(op2);
			code.put(calc);
		}
		return op;
	}

	/**
	 * This method cares about the NTS Factor return a created new Operand
	 */
	private Operand factor() {
		Operand op; // factor needs to return a new Operand
		switch (sym) {
		case ident:
			op = designator();
			if (sym == lpar) {

				if (op.obj.type == Tab.noType) {
					error(INVALID_CALL); // we expected a return value !
				}
				actpars(op);
				if (op.obj == tab.ordObj || op.obj == tab.chrObj) {
					; // nothing
				} else if (op.obj == tab.lenObj)
					code.put(Code.OpCode.arraylength);
				else {
					code.put(Code.OpCode.call);
					code.put2(op.adr - (code.pc - 1));
				}
				// value should be on stack after meth call on stack
				// taken form lecture slides
				op.kind = Operand.Kind.Stack;
			}
			break;
		case number:
			scan(); // scan numer and create constant operand
			op = new Operand(t.val);
			break;
		case charConst:
			scan();// scan numer and create constant operand
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
				op = expr();// get size of array
				// expr has to be type int
				if (op.type != Tab.intType) {
					error(ARRAY_SIZE);
				}
				code.load(op);
				code.put(OpCode.newarray); // create new array
				if (type == Tab.charType) {
					code.put(0); // character type
				} else {
					code.put(1); // integer type
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
			// set operand to cons operand with constant 1
			op = new Operand(1); // factor 1 can't destroy something
			break;
		}
		return op;
	}

	/**
	 * This method cares about the NTS Designator It returns the created Operand
	 * of this Designator
	 */
	private Operand designator() {
		check(ident);
		// t.str is the name of the checked ident
		Operand op = new Operand(tab.find(t.str), this);
		for (;;) {
			if (sym == period) {// field
				if (op.type.kind != Struct.Kind.Class) {
					error(NO_CLASS);// the operand has to be a valid class
				}
				scan();// we know period will happen
				code.load(op);
				check(ident);
				// checks if ident is field of class
				Obj obj = tab.findField(t.str, op.type);
				op.kind = Operand.Kind.Fld; // have to set kind manually
				op.type = obj.type;
				op.adr = obj.adr;
			} else if (sym == lbrack) {// element
				if (op.obj != null && op.obj.kind != Obj.Kind.Var) {
					error(NO_VAL);
				}
				scan();// scan lbrack
				code.load(op);
				Operand op2 = expr(); // get index of array
				if (op.type.kind != Struct.Kind.Arr) {
					error(NO_ARRAY);// the operand has to be a valid array
				}
				if (op2.type != Tab.intType) {
					error(ARRAY_INDEX); // index need to be of type int
				}
				code.load(op2);
				op.kind = Operand.Kind.Elem;// have to set kind manually
				op.type = op.type.elemType;
				check(rbrack);
			} else {
				break; // break from loop
			}
		}
		return op;
	}

	/**
	 * This method cares about the NTS AddOp it return the OpCode of the
	 * addoptoken for codegeneration
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
			error(ADD_OP); // wrong add-operation
			return OpCode.nop;// nop = NO Operation
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
			error(MUL_OP); // wrong mul-operation
			return OpCode.nop; // nop = NO Operation
		}
	}
}
