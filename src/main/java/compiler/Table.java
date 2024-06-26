package compiler;

class RelAddr {
    int level;
    int addr;
    RelAddr(int l, int a) {
        level = l; addr = a;
    }
    public String toString(){
        return String.valueOf(level) + "," + addr;
    }
}

class TableE{  /*　名前表のエントリーの基本の型　*/
    int kind;    /*　名前の種類　*/
    String name; /*　名前のつづり　*/
    TableE() {}
    TableE(String id) { name = id; }
    public String toString(){
        return name + ":" + Table.kindName(kind);
    }
}

class TableEval extends TableE {
    int value;    /*　定数の場合：値　*/
    TableEval(String id, int k, int v){
        name = id; kind = k; value = v;
    }
    public String toString(){
        return super.toString() + ":" + value;
    }
}

class TableEaddr extends TableE {
    RelAddr relAddr;   /*　変数、関数、パラメタのアドレス　*/
    TableEaddr(String id, int k, int level, int addr){
        name =  id; kind = k; relAddr = new RelAddr(level, addr);
    }
    public String toString(){
        return super.toString() + ":" + relAddr.toString();
    }
}

class TableEfunc extends TableEaddr {
    int pars;          /*　関数の場合：パラメタ数　*/
    TableEfunc(int k, int l, int a, int p){
        super("",k, l, a); pars = p;
    }
    TableEfunc(String id, int k, int l, int a, int p){
        super(id, k, l, a); pars = p;
    }
    public String toString(){
        return super.toString() + ":" + pars + " params";
    }
}

class Table {
    GetSource source;
    boolean symTable;
    public Table(GetSource s, boolean printTable) {
        source = s; symTable = printTable;
    }


    /*　Identifierの種類　*/
    static final int 	varId = GetSource.varId;
    static final int funcId = GetSource.funcId;
    static final int parId = GetSource.parId;
    static final int constId = GetSource.constId;

    static final int MAXTABLE = 100;	/*　名前表の最大長さ　*/
    static final int MAXNAME  =  31;		/*　名前の最大長さ　*/
    static final int MAXLEVEL =   5;		/*　ブロックの最大深さ　*/

    TableE[] nameTable = new TableE[MAXTABLE];		/*　名前表　*/
    int tIndex = 0;			/*　名前表のインデックス　*/
    int level = -1;			/*　現在のブロックレベル　*/
    int[] index = new int[MAXLEVEL];   	/*　index[i]にはブロックレベルiの最後のインデックス　*/
    int[] addr  = new int[MAXLEVEL];    	/*　addr[i]にはブロックレベルiの最後の変数の番地　*/
    int localAddr;			/*　現在のブロックの最後の変数の番地　*/
    int tfIndex;        /*　関数名の名前表でのインデックス　*/

    static String kindName(int k)		/*　名前の種類の出力用関数　*/
    {
        switch (k){
            case varId: return "var";
            case parId: return "par";
            case funcId: return "func";
            case constId: return "const";
            default: return "";
        }
    }

    void blockBegin(int firstAddr)	/*　ブロックの始まり(最初の変数の番地)で呼ばれる　*/
    {
        if (level == -1){			/*　主ブロックの時、初期設定　*/
            localAddr = firstAddr;
            tIndex = 0;
            level++;
            return;
        }
        if (level == MAXLEVEL-1)
            source.errorF("too many nested blocks");
        index[level] = tIndex;		/*　今までのブロックの情報を格納　*/
        addr[level] = localAddr;
        localAddr = firstAddr;		/*　新しいブロックの最初の変数の番地　*/
        level++;				/*　新しいブロックのレベル　*/
        return;
    }

    void blockEnd()				/*　ブロックの終りで呼ばれる　*/
    {
        if (symTable) {
            System.out.println("\n ******** Symbol Table of level " + level + " ********");
            int start = (level == 0)? 1 : index[level-1] + 1;
            for (int i = start; i <= tIndex; i++)
                System.out.println(nameTable[i]);
        }
        if(level == 0) return;
        level--;
        tIndex = index[level];		/*　一つ外側のブロックの情報を回復　*/
        localAddr = addr[level];
    }

    int bLevel()				/*　現ブロックのレベルを返す　*/
    {
        return level;
    }

    int fPars()					/*　現ブロックの関数のパラメタ数を返す　*/
    {
        if (level == 0) return 0;
        return ((TableEfunc)nameTable[index[level-1]]).pars;
    }

    void enterT(TableE e)			/*　名前表に名前を登録　*/
    {
        if (tIndex++ < MAXTABLE){
            nameTable[tIndex] = e;
        } else
            source.errorF("too many names");
    }

    int enterTfunc(String id, int v)		/*　名前表に関数名と先頭番地を登録　*/
    {
        TableE e = new TableEfunc(id, funcId, level, v, 0);
        enterT(e);
        tfIndex = tIndex;
        return tIndex;
    }

    int enterTpar(String id)				/*　名前表にパラメタ名を登録　*/
    {
        TableE e = new TableEaddr(id, parId, level, 0);
        enterT(e);
        ((TableEfunc)nameTable[tfIndex]).pars++;  		 /*　関数のパラメタ数のカウント　*/
        return tIndex;
    }

    int enterTvar(String id)			/*　名前表に変数名を登録　*/
    {
        TableE e = new TableEaddr(id, varId, level, localAddr++);
        enterT(e);
        return tIndex;
    }

    int enterTconst(String id, int v)		/*　名前表に定数名とその値を登録　*/
    {
        TableE e = new TableEval(id, constId, v);
        enterT(e);
        return tIndex;
    }

    void endpar()					/*　パラメタ宣言部の最後で呼ばれる　*/
    {
        int i;
        int pars = ((TableEfunc)nameTable[tfIndex]).pars;
        if (pars == 0)  return;
        for (i=1; i<=pars; i++)			/*　各パラメタの番地を求める　*/
            ((TableEaddr)nameTable[tfIndex+i]).relAddr.addr = i-1-pars;
    }

    void changeV(int ti, int newVal)		/*　名前表[ti]の値（関数の先頭番地）の変更　*/
    {
        ((TableEfunc)nameTable[ti]).relAddr.addr = newVal;
    }

    int searchT(String id, int k)		/*　名前idの名前表の位置を返す　*/
        /*　未宣言の時エラーとする　*/
    {
        int i;
        i = tIndex;
        nameTable[0] = new TableE(id);			/*　番兵をたてる　*/
        while( !id.equals(nameTable[i].name) )
            i--;
        if ( i > 0 )							/*　名前があった　*/
            return i;
        else {							/*　名前がなかった　*/
            source.errorType("undef");
            if (k==varId) return enterTvar(id);	/*　変数の時は仮登録　*/
            return 0;
        }
    }

    int kindT(int i)				/*　名前表[i]の種類を返す　*/
    {
        return nameTable[i].kind;
    }

    RelAddr relAddr(int ti)				/*　名前表[ti]のアドレスを返す　*/
    {
        return ((TableEaddr)nameTable[ti]).relAddr;
    }

    int val(int ti)					/*　名前表[ti]のvalueを返す　*/
    {
        return ((TableEval)nameTable[ti]).value;
    }

    int pars(int ti)				/*　名前表[ti]の関数のパラメタ数を返す　*/
    {
        return ((TableEfunc)nameTable[ti]).pars;
    }

    int frameL()				/*　そのブロックで実行時に必要とするメモリー容量　*/
    {
        return localAddr;
    }

}

