package ssw.mj.impl;

import ssw.mj.Errors;
import ssw.mj.Parser;
import ssw.mj.symtab.Obj;
import ssw.mj.symtab.Scope;
import ssw.mj.symtab.Struct;
import ssw.mj.symtab.Tab;

public final class TabImpl extends Tab {

	/**
	 * Set up "universe" (= predefined names).
	 */
	public TabImpl(Parser p) {
		super(p);
		// define predefined type noObj
		noObj = new Obj(Obj.Kind.Var, "noObj", noType);

		openScope(); // opens the univers scope on level -1
		// standard types
		insert(Obj.Kind.Type, "int", intType);
		insert(Obj.Kind.Type, "char", charType);
		insert(Obj.Kind.Con, "null", nullType);

		// chr - Method
		chrObj = insert(Obj.Kind.Meth, "chr", charType);
		openScope();// open new scope for creating list
		Obj objI = insert(Obj.Kind.Var, "i", intType);
		chrObj.nPars = 1; // sets number of parameters manually
		objI.level = 1;// set level to local manually
		chrObj.locals = curScope.locals();// relink locals from created scope
		closeScope();// get back to universe scope

		// ord - Method
		ordObj = insert(Obj.Kind.Meth, "ord", intType);
		openScope();// open new scope for creating list
		Obj chObj = insert(Obj.Kind.Var, "ch", charType);
		ordObj.nPars = 1; // sets number of parameters manually
		ordObj.locals = curScope.locals(); // relink locals from created scope
		chObj.level = 1;// set level to local manually
		closeScope();// get back to universe scope

		// len - Method
		lenObj = insert(Obj.Kind.Meth, "len", intType);
		openScope(); // open new scope for creating list
		Obj arrObj = insert(Obj.Kind.Var, "arr", new StructImpl(noType));
		lenObj.nPars = 1;// sets number of parameters manually
		arrObj.level = 1; // set level to local manually
		lenObj.locals = curScope.locals();// relink locals from created scope
		closeScope();// get back to universe scope
	}

	/**
	 * open a new scope
	 */
	public void openScope() {
		Scope s = new Scope(curScope);
		curScope = s;
		curLevel++;
	}

	/**
	 * closes current scope
	 */
	public void closeScope() {
		curScope = curScope.outer();
		curLevel--;
	}

	/**
	 * Insert new Obj into current scope
	 * 
	 * @param kind
	 * @param name
	 * @param type
	 * @return created Obj
	 */
	public Obj insert(Obj.Kind kind, String name, StructImpl type) {
		if (name == null || name.equals("")) {
			return noObj;
		}
		Obj obj = new Obj(kind, name, type);

		if (kind == Obj.Kind.Var) { // if Kind is Var, set the level and adr
			obj.adr = curScope.nVars();
			obj.level = curLevel;
		}
		if (curScope.findLocal(name) == null) {
			curScope.insert(obj); // inserts obj in list and if var does nVars++
		} else {
			parser.error(Errors.Message.DECL_NAME, name);
		}
		return obj;
	}

	/**
	 * This method checks for a defined entry with the given key "name" Throws
	 * an error Message NOT_FOUND, if there is no certain entry
	 * 
	 * @param name
	 * @return
	 */
	public Obj find(String name) {
		Obj obj = curScope.findGlobal(name); // checks local scopes recursively
		if (obj != null) {
			return obj;
		} // found!
		parser.error(Errors.Message.NOT_FOUND, name);
		return noObj;
	}

	/**
	 * This method checks for an entry with the key "name" if found, it needs to
	 * check, if the type is set correctly
	 * 
	 * @param name
	 * @param type
	 * @return
	 */
	public Obj findField(String name, Struct type) {

		Obj obj = type.findField(name); // checks local scopes recursively
		if (obj != null) {
			return obj; // found!
		}
		parser.error(Errors.Message.NO_FIELD, name);
		return noObj;
	}

}
