package ssw.mj.impl;

import ssw.mj.Scanner;
import ssw.mj.Token;
import ssw.mj.Token.Kind;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import static ssw.mj.Errors.Message.*;
import static ssw.mj.Token.Kind.*;

public final class ScannerImpl extends Scanner {

	private final Map<String, Kind> keywordMap;
	private final int LINESTART = 1; // used for init
	private final int COLSTART = 0;// used for init and resetting

	public ScannerImpl(Reader r) {
		super(r);
		line = LINESTART; // init lines, col
		col = COLSTART;
		nextCh(); // skip first character
		keywordMap = new HashMap<>(); // stores keyword lookup for readName()
		defineKeywordList(); // stores the keyword in the HashMap
	}

	/**
	 * Returns next token. To be used by parser.
	 */
	@Override
	public Token next() {
		while (Character.isWhitespace(ch)) {
			nextCh();// skip white space
		}
		Token t = new Token(none, line, col);
		switch (ch) {
		// ----- identifier or keyword
		case 'a':
		case 'b':
		case 'c':
		case 'd':
		case 'e':
		case 'f':
		case 'g':
		case 'h':
		case 'i':
		case 'j':
		case 'k':
		case 'l':
		case 'm':
		case 'n':
		case 'o':
		case 'p':
		case 'q':
		case 'r':
		case 's':
		case 't':
		case 'u':
		case 'v':
		case 'w':
		case 'x':
		case 'y':
		case 'z':
		case 'A':
		case 'B':
		case 'C':
		case 'D':
		case 'E':
		case 'F':
		case 'G':
		case 'H':
		case 'I':
		case 'J':
		case 'K':
		case 'L':
		case 'M':
		case 'N':
		case 'O':
		case 'P':
		case 'Q':
		case 'R':
		case 'S':
		case 'T':
		case 'U':
		case 'V':
		case 'W':
		case 'X':
		case 'Y':
		case 'Z':
			readName(t); // distinguishes between identifier and keyword
			break;
		// ----- number
		case '0':
		case '1':
		case '2':
		case '3':
		case '4':
		case '5':
		case '6':
		case '7':
		case '8':
		case '9':
			readNumber(t);
			break;
		// ----- char konstant
		case '\'': // charconstants
			t.kind = charConst;
			readCharConst(t);
			break;
		case '/': // possible comment
			nextCh();
			if (ch == '*') {
				skipComment(t);
				t = next();/* recursion */
			} else if (ch == '=') {
				t.kind = slashas;
				nextCh();
			} else {
				t.kind = slash;
			}
			break;
		// ----- simple tokens
		case EOF:
			t.kind = eof;
			/* no nextCh() */
			break;
		case '+':
			nextCh();
			if (ch == '+') {
				t.kind = pplus;
				nextCh();
			} else if (ch == '=') {
				t.kind = plusas;
				nextCh();
			} else {
				t.kind = plus;
			}
			break;
		case '-':
			nextCh();
			if (ch == '-') {
				t.kind = mminus;
				nextCh();
			} else if (ch == '=') {
				t.kind = minusas;
				nextCh();
			} else {
				t.kind = minus;
			}
			break;
		case '*':
			nextCh();
			if (ch == '=') {
				t.kind = timesas;
				nextCh();
			} else {
				t.kind = times;
			}
			break;
		case '%':
			nextCh();
			if (ch == '=') {
				t.kind = remas;
				nextCh();
			} else {
				t.kind = rem;
			}
			break;
		case '=':
			nextCh();
			if (ch == '=') {
				t.kind = eql;
				nextCh();
			} else {
				t.kind = assign;
			}
			break;
		case '!':
			nextCh();
			if (ch == '=') {
				t.kind = neq;
				nextCh();
			} else {
				error(t, INVALID_CHAR, '!');
			}
			break;
		case '<':
			nextCh();
			if (ch == '=') {
				t.kind = leq;
				nextCh();
			} else {
				t.kind = lss;

			}
			break;
		case '>':
			nextCh();
			if (ch == '=') {
				t.kind = geq;
				nextCh();
			} else {
				t.kind = gtr;

			}
			break;
		case '&':
			nextCh();
			if (ch == '&') {
				t.kind = and;
				nextCh();
			} else {
				error(t, INVALID_CHAR, '&');
			}
			break;
		case '|':
			nextCh();
			if (ch == '|') {
				t.kind = or;
				nextCh();
			} else {
				error(t, INVALID_CHAR, '|');
			}
			break;
		case ';':
			t.kind = semicolon;
			nextCh();
			break;
		case ':':
			t.kind = colon;
			nextCh();
			break;
		case ',':
			t.kind = comma;
			nextCh();
			break;
		case '.':
			t.kind = period;
			nextCh();
			break;
		case '(':
			t.kind = lpar;
			nextCh();
			break;
		case ')':
			t.kind = rpar;
			nextCh();
			break;
		case '[':
			t.kind = lbrack;
			nextCh();
			break;
		case ']':
			t.kind = rbrack;
			nextCh();
			break;
		case '{':
			t.kind = lbrace;
			nextCh();
			break;
		case '}':
			t.kind = rbrace;
			nextCh();
			break;

		default:
			error(t, INVALID_CHAR, ch);
			nextCh();
			break;

		}
		return t;
	}

	/**
	 * This method is skipping comments and boxed comments
	 * 
	 * @param t
	 */
	private void skipComment(Token t) {
		int openComments = 1; // only if all comments get closed
		nextCh(); // now we are after the first '/*'
		char last = ch; // save char before current ch
		while (openComments > 0 && ch != EOF) {
			nextCh(); // go to next symbol
			if (last == '/' && ch == '*') {
				openComments++; // we started a new comment
				nextCh(); // we need to step symobl further
			} else if (last == '*' && ch == '/') {
				openComments--;// we ended a comment
				nextCh();// we need to step symobl further
			}
			last = ch; // store current char
		}

		// the while loop is over, check if we got
		// EOF BEFORE comments were closed
		if (openComments > 0) {
			error(t, EOF_IN_COMMENT);
		}
	}

	/**
	 * This methods handles CharConstants
	 * 
	 * @param t
	 */
	private void readCharConst(Token t) {
		nextCh(); // get next char after the first '
		if (ch == '\'') { // check if there is a constant
			error(t, EMPTY_CHARCONST);
			nextCh();// go to next character for switchcase
		} else if (ch == LF) { // check if there is a linefeed
			error(t, ILLEGAL_LINE_END);
			nextCh();// go to next character for switchcase
		} else if (ch == '\\') {// check if corr. escape sequ is used
			nextCh(); // get next char for sequence check
			if (ch != 'r' && ch != 'n' && ch != '\'' && ch != '\\') {
				error(t, UNDEFINED_ESCAPE, ch);
			} else { // here we know, we got a corr. escape seq. -> assign corr
						// one
				if (ch == 'r')
					t.val = '\r';
				if (ch == 'n')
					t.val = '\n';
				if (ch == '\'')
					t.val = '\'';
				if (ch == '\\')
					t.val = '\\';
			}
			nextCh(); // get quote - its hopefully there
			if (ch != '\'') {
				error(t, MISSING_QUOTE);
			} else {
				nextCh(); // go to next character for switchcase
			}
		} else { // there is a unspecial char in the first quote
			char temp = ch;
			nextCh();
			if (ch != '\'') { // after one char there was no quote
				error(t, MISSING_QUOTE);
			} else {
				// correct char constant
				t.val = temp;
				nextCh();// go to next character for switchcase
			}

		}

	}

	/**
	 * This method takes a token a creates a new number If number bigger than
	 * :2147483647 -> Error
	 * 
	 * @param t
	 */
	private void readNumber(Token t) {
		StringBuilder nameReader = new StringBuilder();
		nameReader.append(ch); // append char from switchCase in next() method
		nextCh();
		while (Character.isDigit(ch)) { // read until there are no more digits
			nameReader.append(ch);
			nextCh();
		}
		t.kind = number; // this tokken is a number tokken
		try {
			t.val = Integer.parseInt(nameReader.toString());
		} catch (NumberFormatException ex) { // if the number is too big
			error(t, BIG_NUM, nameReader.toString());
		}
	}

	/**
	 * This method takes a token a creates a new name or check if it is a
	 * keyword
	 * 
	 * @param t
	 */
	private void readName(Token t) {
		StringBuilder nameReader = new StringBuilder();
		nameReader.append(ch); // append char from switchCase in next() method
		nextCh();
		// read until no valid symbol is read anymore
		while (Character.isLetter(ch) || Character.isDigit(ch) || ch == '_') {
			nameReader.append(ch);
			nextCh();
		}
		// now we are finished reading the name, now check if keyword
		String tempStr = nameReader.toString();
		if (keywordMap.containsKey(tempStr)) { // keyword
			t.kind = keywordMap.get(tempStr);
		} else { // no keyword
			t.kind = ident;
			t.str = tempStr; // sets ident string
		}
	}

	/**
	 * This Method reads next Char with Reader
	 */
	private void nextCh() {
		try {
			ch = (char) in.read();
			col++;
			if (ch == '\r') {
				nextCh();
			} else if (ch == LF) {
				line++;
				col = COLSTART;
			} else if (ch == EOF) {
				col--;
			}
		} catch (IOException e) {
			ch = EOF;
		}
	}

	/**
	 * Fills HashMap List
	 */
	private void defineKeywordList() {
		keywordMap.put("break", break_);
		keywordMap.put("class", class_);
		keywordMap.put("else", else_);
		keywordMap.put("final", final_);
		keywordMap.put("if", if_);
		keywordMap.put("new", new_);
		keywordMap.put("print", print);
		keywordMap.put("program", program);
		keywordMap.put("read", read);
		keywordMap.put("return", return_);
		keywordMap.put("void", void_);
		keywordMap.put("while", while_);
		keywordMap.put("loop", loop_);
	}
}
