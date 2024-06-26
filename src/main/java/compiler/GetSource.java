package compiler;

import java.io.*;
import java.lang.*;

class Token {
    int kind;
    String id;
    int value;
    public String toString(){
        if (kind < GetSource.end_of_KeySym)
            return GetSource.keyWdT[kind];
        else if (kind == GetSource.Id)
            return id;
        else //(kind == Num)
            return String.valueOf(value) ;
    }
}

public class GetSource {

    static final int MAXLINE = 120;
    static final int MAXERROR = 30;
    static final int MAXNUM = 14;
    static final int MAXNAME = 31;
    static final int TAB = 5;

    static final String INSERT_C = "#0000FF";  /* 挿入文字の色 */
    static final String DELETE_C = "#FF0000";  /* 削除文字の色 */
    static final String TYPE_C = "#00FF00";  /* タイプエラー文字の色 */

    static final int Begin = 0;
    static final int End = 1;
    static final int If= 2;
    static final int Then = 3;
    static final int While = 4;
    static final int Do = 5;
    static final int Ret = 6;
    static final int Func = 7;
    static final int Var = 8;
    static final int Const = 9;
    static final int Odd  = 10;
    static final int Write  = 11;
    static final int Writeln  = 12;
    static final int end_of_KeyWd  = 13;
    /*　予約語の名前はここまで　*/
    /*　演算子と区切り記号の名前　*/
    static final int  Plus = 14;
    static final int  Minus = 15;
    static final int  Mult = 16;
    static final int  Div = 17;
    static final int  Lparen = 18;
    static final int  Rparen = 19;
    static final int  Equal = 20;
    static final int  Lss = 21;
    static final int  Gtr = 22;
    static final int  NotEq = 23;
    static final int  LssEq = 24;
    static final int  GtrEq = 25;
    static final int  Comma = 26;
    static final int  Period = 27;
    static final int  Semicolon = 28;
    static final int  Assign = 29;
    static final int  end_of_KeySym = 30;
    /*　演算子と区切り記号の名前はここまで　*/
    /*　トークンの種類　*/
    static final int  Id = 31;
    static final int  Num = 32;
    static final int  nul = 33;
    static final int  end_of_Token = 34;
    /*　トークンの種類はここまで　*/
    /*　文字の種類　*/
    static final int  letter = 35;
    static final int  digit = 36;
    static final int  colon = 37;
    /*　上記以外の文字の種類　*/
    static final int  others = 38;

    /*　名前の種類　*/
    static final int  constId = 1;
    static final int  varId = 2;
    static final int  parId = 3;
    static final int  funcId = 4;

    BufferedReader fpi;   /*　ソースファイル　*/
    PrintWriter  fpo;     /*　html出力ファイル　*/
    String line;      /*　１行分の入力バッファー　*/
    int lineIndex;    /*　次に読む文字の位置　*/
    char ch;          /*　最後に読んだ文字　*/

    Token cToken;     /*　最後に読んだトークン　*/
    int idKind;       /*　現トークン(Id)の種類　*/
    int spaces, CR;   /*　そのトークンの前のスペースと改行の個数　*/
    boolean printed;  /*　トークンは印字済みか　*/

    int errorNo;      /*　出力したエラーの数　*/

    static String[] keyWdT = new String[end_of_KeySym +1];
    /*　予約語や記号と名前(KeyId)の表　*/
    void initKeyWdT() {
        keyWdT[Begin] = "begin".intern();
        keyWdT[End] = "end".intern();
        keyWdT[If] = "if".intern();
        keyWdT[Then] = "then".intern();
        keyWdT[While] = "while".intern();
        keyWdT[Do] = "do".intern();
        keyWdT[Ret] = "return".intern();
        keyWdT[Func] = "function".intern();
        keyWdT[Var] = "var".intern();
        keyWdT[Const] = "const".intern();
        keyWdT[Odd] = "odd".intern();
        keyWdT[Write] = "write".intern();
        keyWdT[Writeln] = "writeln".intern();
        keyWdT[end_of_KeyWd] = "$dummy1";
        /*　記号と名前(KeyId)の表　*/
        keyWdT[Plus] = "+";
        keyWdT[Minus] = "-";
        keyWdT[Mult] = "*";
        keyWdT[Div] = "/";
        keyWdT[Lparen] = "(";
        keyWdT[Rparen] = ")";
        keyWdT[Equal] = "=";
        keyWdT[Lss] = "<";
        keyWdT[Gtr] = ">";
        keyWdT[NotEq] = "<>";
        keyWdT[LssEq] = "<=";
        keyWdT[GtrEq] = ">=";
        keyWdT[Comma] = ",";
        keyWdT[Period] = ".";
        keyWdT[Semicolon] = ";";
        keyWdT[Assign] = ":=";
        keyWdT[end_of_KeySym] = "$dummy2";
    }

    Table table;

    void set(Table t) {
        table = t;
    }

    boolean isKeyWd(int k)			/*　キーkは予約語か？　*/
    {
        return (k < end_of_KeyWd);
    }

    boolean isKeySym(int k)		/*　キーkは記号か？　*/
    {
        if (k < end_of_KeyWd)
            return false;
        return (k < end_of_KeySym);
    }

    int[] charClassT = new int[256];		/*　文字の種類を示す表にする　*/

    void initCharClassT()		/*　文字の種類を示す表を作る関数　*/
    {
        int i;
        for (i=0; i<256; i++)
            charClassT[i] = others;
        for (i='0'; i<='9'; i++)
            charClassT[i] = digit;
        for (i='A'; i<='Z'; i++)
            charClassT[i] = letter;
        for (i='a'; i<='z'; i++)
            charClassT[i] = letter;
        charClassT['+'] = Plus; charClassT['-'] = Minus;
        charClassT['*'] = Mult; charClassT['/'] = Div;
        charClassT['('] = Lparen; charClassT[')'] = Rparen;
        charClassT['='] = Equal; charClassT['<'] = Lss;
        charClassT['>'] = Gtr; charClassT[','] = Comma;
        charClassT['.'] = Period; charClassT[';'] = Semicolon;
        charClassT[':'] = colon;
    }

    boolean openSource(String fileName) 		/*　ソースファイルのopen　*/
    {
        try {
            fpi = new BufferedReader (new  FileReader(fileName));
            fpo = new PrintWriter (new FileWriter(fileName + ".html"));
        } catch (IOException e) {
            System.out.println("can't open " + fileName);
            return false;
        }
        return true;
    }

    void closeSource()				 /*　ソースファイルと.htmlファイルをclose　*/
    {
        try { fpi.close();
        } catch (IOException e) {
            System.out.println("can't close source file");
        }
        fpo.close();
    }

    void initSource()
    {
        lineIndex = -1;				 /*　初期設定　*/
        ch = '\n';
        printed = true;
        initCharClassT();
        initKeyWdT();

        fpo.print("<HTML>\n");   /*　htmlコマンド　*/
        fpo.print("<HEAD>\n<TITLE>compiled source program</TITLE>\n</HEAD>\n");
        fpo.print("<BODY>\n<PRE>\n");
    }

    void finalSource()
    {
        if (cToken.kind==Period)
            printcToken();
        else
            errorInsert(Period);
        fpo.print("\n</PRE>\n</BODY>\n</HTML>\n");
    }

    void errorNoCheck()			/*　エラーの個数のカウント、多すぎたら終わり　*/
    {
        if (errorNo++ > MAXERROR){
            fpo.print("too many errors\n</PRE>\n</BODY>\n</HTML>\n");
            System.out.print("abort compilation\n");
            System.exit(1);
        }
    }

    void errorType(String m)		/*　型エラーを.htmlファイルに出力　*/
    {
        printSpaces();
        fpo.print("<FONT COLOR=" + TYPE_C + ">" + m + "</FONT>");
        printcToken();
        errorNoCheck();
    }


    void errorInsert(int k)		/*　keyString(k)を.htmlファイルに挿入　*/
    {
        fpo.print("<FONT COLOR=" + INSERT_C + "><b>" + keyWdT[k] + "</b></FONT>");
        errorNoCheck();
    }

    void errorMissingId()			/*　名前がないとのメッセージを.htmlファイルに挿入　*/
    {
        fpo.print("<FONT COLOR=" + INSERT_C + ">Id</FONT>");
        errorNoCheck();
    }

    void errorMissingOp()		/*　演算子がないとのメッセージを.htmlファイルに挿入　*/
    {
        fpo.print("<FONT COLOR=" + INSERT_C + ">@</FONT>");
        errorNoCheck();
    }

    void errorDelete()			/*　今読んだトークンを読み捨てる　*/
    {
        int i = cToken.kind;
        printSpaces();
        printed = true;
        if (i < end_of_KeyWd) 							/*　予約語　*/
            fpo.print("<FONT COLOR=" + DELETE_C + "><b>" + keyWdT[i] + "</b></FONT>");
        else if (i < end_of_KeySym)					/*　演算子か区切り記号　*/
            fpo.print("<FONT COLOR=" + DELETE_C + ">" + keyWdT[i] + "</FONT>");
        else if (i == Id)								/*　Identfier　*/
            fpo.print("<FONT COLOR=" + DELETE_C + ">" + cToken.id + "</FONT>");
        else if (i == Num)								/*　Num　*/
            fpo.print("<FONT COLOR=" + DELETE_C + ">" + cToken.value + "</FONT>");
    }

    void errorMessage(String m)	/*　エラーメッセージを.htmlファイルに出力　*/
    {
        fpo.print("<FONT COLOR=" + TYPE_C + ">" + m + "</FONT>");
        errorNoCheck();
    }

    void errorF(String m)			/*　エラーメッセージを出力し、コンパイル終了　*/
    {
        errorMessage(m);
        fpo.print("fatal errors\n</PRE>\n</BODY>\n</HTML>\n");
        if (errorNo > 0)
            System.out.println("total " + errorNo + " errors");
        System.out.println("abort compilation\n");
        System.exit(1);
    }

    int errorN()				/*　エラーの個数を返す　*/
    {
        return errorNo;
    }

    char nextChar()				/*　次の１文字を返す関数　*/
    {
        if (lineIndex == -1){
            try {
                line = fpi.readLine(); lineIndex = 0;
            } catch (IOException e) {System.out.println(e);}
            if ( line == null){
                errorF("end of file\n");      /* end of fileならコンパイル終了 */
            }
        }
        if (line.length()-1 < lineIndex ) {
            lineIndex = -1;		/*　行の終わりまで行っていたら、次の行の入力準備　*/
            return '\n';				/*　文字としては改行文字を返す　*/
        }
        return line.charAt(lineIndex++);
    }

    Token nextToken()			/*　次のトークンを読んで返す関数　*/
    {
        int i = 0;
        int num;
        int cc;
        Token temp = new Token();
        char[] ident = new char[MAXNAME];
        printcToken(); 			/*　前のトークンを印字　*/
        spaces = 0;  CR = 0;
        while (true){				/*　次のトークンまでの空白や改行をカウント　*/
            if (ch == ' ')
                spaces++;
            else if	(ch == '\t')
                spaces += TAB;
            else if (ch == '\n'){
                spaces = 0;  CR++;
            }
            else break;
            ch = nextChar();
        }
        switch (cc = charClassT[ch]) {
            case letter: 				/* identifier */
                do {
                    if (i < MAXNAME)
                        ident[i] = ch;
                    i++; ch = nextChar();
                } while (  charClassT[ch] == letter
                        || charClassT[ch] == digit );
                if (i >= MAXNAME){
                    errorMessage("too long");
                    i = MAXNAME - 1;
                }
                String id = new String(ident,0,i).intern(); //identをStringに変換する
                for (i=0; i<end_of_KeyWd; i++)
                    if (id == keyWdT[i]) {
                        temp.kind = i;  		/*　予約語の場合　*/
                        cToken = temp; printed = false;
                        return temp;
                    }
                temp.kind = Id;		/*　ユーザの宣言した名前の場合　*/
                temp.id = id;
                break;
            case digit: 					/* number */
                num = 0;
                do {
                    num = 10*num+(ch-'0');
                    i++; ch = nextChar();
                } while (charClassT[ch] == digit);
                if (i>MAXNUM)
                    errorMessage("too large");
                temp.kind = Num;
                temp.value = num;
                break;
            case colon:
                if ((ch = nextChar()) == '=') {
                    ch = nextChar();
                    temp.kind = Assign;		/*　":="　*/
                    break;
                } else {
                    temp.kind = nul;
                    break;
                }
            case Lss:
                if ((ch = nextChar()) == '=') {
                    ch = nextChar();
                    temp.kind = LssEq;		/*　"<="　*/
                    break;
                } else if (ch == '>') {
                    ch = nextChar();
                    temp.kind = NotEq;		/*　"<>"　*/
                    break;
                } else {
                    temp.kind = Lss;
                    break;
                }
            case Gtr:
                if ((ch = nextChar()) == '=') {
                    ch = nextChar();
                    temp.kind = GtrEq;		/*　">="　*/
                    break;
                } else {
                    temp.kind = Gtr;
                    break;
                }
            default:
                temp.kind = cc;
                ch = nextChar(); break;
        }
        cToken = temp; printed = false;
        return temp;
    }

    Token checkGet(Token t, int k)			/*　t.kind == k のチェック　*/
        /*　t.kind == k なら、次のトークンを読んで返す　*/
        /*　t.kind != k ならエラーメッセージを出し、t と k が共に記号、または予約語なら　*/
        /*　t を捨て、次のトークンを読んで返す（ t を k で置き換えたことになる）　*/
        /*　それ以外の場合、k を挿入したことにして、t を返す　*/
    {
        if (t.kind == k)
            return nextToken();
        if ((isKeyWd(k) && isKeyWd(t.kind)) ||
                (isKeySym(k) && isKeySym(t.kind))){
            errorDelete();
            errorInsert(k);
            return nextToken();
        }
        errorInsert(k);
        return t;
    }

    void printSpaces()			/*　空白や改行の印字　*/
    {
        while (CR-- > 0)
            fpo.print("\n");
        while (spaces-- > 0)
            fpo.print(" ");
        CR = 0; spaces = 0;
    }

    void printcToken()				/*　現在のトークンの印字　*/
    {
        if (printed){
            printed = false; return;
        }
        int i=cToken.kind;
        printed = true;
        printSpaces();				/*　トークンの前の空白や改行印字　*/
        if (i < end_of_KeyWd) 						/*　予約語　*/
            fpo.print("<b>" + keyWdT[i] + "</b>");
        else if (i < end_of_KeySym)					/*　演算子か区切り記号　*/
            fpo.print(keyWdT[i]);
        else if (i == Id){
            switch (idKind) {
                case varId:
                    fpo.print(cToken.id); return;
                case parId:
                    fpo.print("<i>" + cToken.id + "</i>"); return;
                case funcId:
                    fpo.print("<i>" + cToken.id + "</i>"); return;
                case constId:
                    fpo.print("<tt>" + cToken.id + "</tt>"); return;
            }
        }else if (i == Num)			/*　Num　*/
            fpo.print(cToken.value);
    }

    void setIdKind (int k)			/*　現トークン(Id)の種類をセット　*/
    {
        idKind = k;
    }
}

