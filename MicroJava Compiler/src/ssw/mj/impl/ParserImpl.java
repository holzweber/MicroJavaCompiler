package ssw.mj.impl;

import java.util.EnumSet;

import ssw.mj.Errors.Message;
import ssw.mj.Parser;
import ssw.mj.Scanner;
import ssw.mj.Token;
import ssw.mj.Token.Kind;
import ssw.mj.symtab.Tab;
import ssw.mj.symtab.Obj;
import ssw.mj.symtab.Struct;

import static ssw.mj.Token.Kind.*;
import static ssw.mj.Errors.Message.*;

public final class ParserImpl extends Parser {

	private int errordistance = 3;
	/*
	 * define needed EnumSets, for later faster checking EnumSets are only
	 * created, if we would need more than one check per First comparison of
	 * NTS.
	 */
	private static final EnumSet<Kind> firstMethodDecl, firstStatement,
			firstAssignop, firstRelop, firstExpr, firstAddop, firstMulop,
			declProgram, followDecl, followStat, followMethodDecl;

	/**
	 * static constructor, for initializing the enumsets above
	 */
	static {
		firstMethodDecl = EnumSet.of(ident, void_);
		firstStatement = EnumSet.of(ident, if_, loop_, while_, break_, return_,
				read, print, lbrace, semicolon);
		firstAssignop = EnumSet.of(assign, plusas, minusas, timesas, slashas,
				remas);
		firstRelop = EnumSet.of(eql, neq, lss, leq, gtr, geq);
		firstExpr = EnumSet.of(minus, ident, number, charConst, new_, lpar);
		firstAddop = EnumSet.of(plus, minus);
		firstMulop = EnumSet.of(times, slash, rem);
		declProgram = EnumSet.of(final_, class_, ident);

		// follows enum sets
		followDecl = EnumSet.of(lbrace, final_, class_, ident, eof);
		followStat = EnumSet.of(rbrace, if_, loop_, while_, break_, return_,
				read, print, semicolon, eof); // without lbrace and ident
		followMethodDecl = EnumSet.of(ident, void_, rbrace, eof);
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

	private void recoverDecl(Message errorMsg) {
		if (errordistance >= 3) {
			error(errorMsg);
			errordistance = 0;
		}
		do {
			scan();
		} while (!followDecl.contains(sym));
	}

	private void recovcerMethodDecl() {

	}

	private void recoverStat() {
		if (errordistance >= 3) {
			error(INVALID_STAT);
			errordistance = 0;
		}
		do {
			scan();
		} while (!followStat.contains(sym));
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

	private void scan() {
		t = la; // reset the current tokens
		la = scanner.next(); // get next token(one ahead)
		sym = la.kind; // for better usage of kind
		errordistance++;
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
		while (declProgram.contains(sym)) {
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
			default:// dont need default, because we check enumset, just for
					// supress warning
				break;
			}
		}
		if (tab.curScope.nVars() > MAX_GLOBALS) {
			error(TOO_MANY_GLOBALS);
		}
		check(lbrace);
		// loop as long as new method starts
		while (firstMethodDecl.contains(sym)) {
			methoddecl();
		}
		check(rbrace);
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
			scan(); // we already now, the next one is valid
			o.val = t.val; // set value of cosntant
		} else {
			error(CONST_DECL); // const decl is not used corr.
		}
		check(semicolon);
	}

	/**
	 * This method cares about the NTS VarDecl
	 */
	private void vardecl() {
		StructImpl type = type();
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
		StructImpl type = Tab.noType;
		Kind returnType = sym; // store return Type of method
		if (sym == ident) {
			type = type();
		} else if (sym == void_) {
			scan();
		} else {
			error(METH_DECL);
		}
		check(ident);
		String methodName = t.str; // store method name could be main
		Obj meth = tab.insert(Obj.Kind.Meth, t.str, type);
		check(lpar);
		boolean tempFormPars = false; // check for formPars
		tab.openScope();
		// optional form pars
		if (sym == ident) {
			formpars();
			tempFormPars = true;
		}
		check(rpar);

		meth.nPars = tab.curScope.nVars(); // save curr number of formpars
		// we need to check the main method
		if ("main".equals(methodName)) {
			if (tempFormPars) { // main is not allowed to have formpars!
				error(MAIN_WITH_PARAMS);
			} else if (returnType == ident) { // check if returnType is void
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
		block();
		tab.closeScope();
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
		// There can be arbitrary much statements inside a block
		while (firstStatement.contains(sym)) {// check if a new statement starts
			statement();
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
			designator();
			switch (sym) {
			case assign:
			case plusas:
			case minusas:
			case timesas:
			case slashas:
			case remas:
				assignop();
				expr();
				break;
			case lpar:
				actpars();
				break;
			case pplus:
				scan();
				break;
			case mminus:
				scan();
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
				Obj obj = tab.find(t.str); // check if ident is defined
				if (obj.kind != Obj.Kind.Label) {// check if ident is loop label
					error(NO_LABEL);
				}
			}
			check(semicolon);
			break;
		case return_:
			scan();
			if (firstExpr.contains(sym)) {
				expr();
			}
			check(semicolon);
			break;
		case read:
			scan();
			check(lpar);
			designator();
			check(rpar);
			check(semicolon);
			break;
		case print:
			scan();
			check(lpar);
			expr();
			if (sym == comma) {
				scan();// we know colon happened here
				check(number);
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
			error(INVALID_STAT, sym);
		}
	}

	/**
	 * This method cares about the NTS AssignOp
	 */
	private void assignop() {
		if (firstAssignop.contains(sym)) {// check if valid assignop was used
			scan();// scan assign token
		} else {
			error(ASSIGN_OP, sym);
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
	private void expr() {
		if (sym == minus) {
			scan();// we know minus happened
		}
		term();
		while (firstAddop.contains(sym)) {
			addop();
			term();
		}
	}

	/**
	 * This method cares about the NTS Term
	 */
	private void term() {
		factor();
		while (firstMulop.contains(sym)) {
			mulop();
			factor();
		}
	}

	/**
	 * This method cares about the NTS Factor
	 */
	private void factor() {
		switch (sym) {
		case ident:
			designator();
			if (sym == lpar) {
				actpars();
			}
			break;
		case number:
			scan();
			break;
		case charConst:
			scan();
			break;
		case new_:
			scan();
			check(ident);
			if (sym == lbrack) {
				scan();
				expr();
				check(rbrack);
			}
			break;
		case lpar:
			scan();
			expr();
			check(rpar);
			break;
		default:
			error(INVALID_FACT);
			break;
		}
	}

	/**
	 * This method cares about the NTS Designator
	 */
	private void designator() {
		check(ident);
		for (;;) {
			if (sym == period) {
				scan();// we know period will happen
				check(ident);
			} else if (sym == lbrack) {
				scan();// we know lbrack will happen
				expr();
				check(rbrack);
			} else {
				break; // break from loop
			}
		}
	}

	/**
	 * This method cares about the NTS AddOp
	 */
	private void addop() {
		if (firstAddop.contains(sym)) {
			scan();// we know correct addop will happen
		} else {// invalid addop
			error(ADD_OP, sym);
		}
	}

	/**
	 * This method cares about the NTS MulOp
	 */
	private void mulop() {
		if (firstMulop.contains(sym)) {// we know correct mulop will happen
			scan();
		} else {// invalid mulop
			error(MUL_OP, sym);
		}
	}
}
