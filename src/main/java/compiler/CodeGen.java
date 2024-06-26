package compiler;

public class CodeGen {
    GetSource source;
    Table table;
    boolean trace;
    public CodeGen(GetSource s, Table t, boolean trace) {
        source = s;
        table = t;  this.trace = trace;
    }

    /*　命令語のコード　*/
    static final int lit = 1 ;
    static final int opr = 2;
    static final int lod = 3;
    static final int sto = 4;
    static final int cal = 5;
    static final int ret = 6;
    static final int ict = 7;
    static final int jmp = 8;
    static final int jpc = 9;

    /*　演算命令のコード　*/
    static final int neg = 10;
    static final int add = 11;
    static final int sub = 12;
    static final int mul = 13;
    static final int div = 14;
    static final int odd = 15;
    static final int eq  = 16;
    static final int ls  = 17;
    static final int gr  = 18;
    static final int neq = 19;
    static final int lseq = 20;
    static final int greq = 21;
    static final int wrt  = 22;
    static final int wrl  = 23;

    static final int MAXCODE = 200; 	/*　目的コードの最大長さ　*/
    static final int  MAXMEM = 2000;  /*　実行時スタックの最大長さ　*/
    static final int   MAXREG = 20;    /*　演算レジスタスタックの最大長さ　*/
    static final int   MAXLEVEL =5;    /*　ブロックの最大深さ　*/

    /*　命令語の型　*/
    class Inst {      /*　命令語の基本の型　*/
        int opCode;
        Inst(int op) { opCode = op; }
    }

    class InstAddr extends Inst{   /*　アドレス部を持つ命令語の型　*/
        RelAddr addr;
        InstAddr(int op, RelAddr a) {super(op); addr = a; }
    }

    class InstVal extends Inst{    /*　値部を持つ命令語の型　*/
        int value;
        InstVal(int op, int v) {super(op); value = v; }
    }

    class InstOp extends Inst{     /*　演算命令の命令語の型　*/
        int optr;
        InstOp(int op, int o) {super(op); optr = o; }
    }

    Inst code[] = new Inst[MAXCODE];		/*　目的コードが入る　*/
    int cIndex = -1;				/*　最後に生成した命令語のインデックス　*/

    int nextCode()					/*　次の命令語のアドレスを返す　*/
    {
        return cIndex+1;
    }

    int genCodeV(int op, int v)		/*　命令語の生成、アドレス部にv　*/
    {
        checkMax();
        code[cIndex] = new InstVal(op, v);
        return cIndex;
    }

    int genCodeT(int op, int ti)		/*　命令語の生成、アドレスは名前表から　*/
    {
        checkMax();
        code[cIndex] = new InstAddr(op, table.relAddr(ti));
        return cIndex;
    }

    int genCodeO(int p)			/*　命令語の生成、アドレス部に演算命令　*/
    {
        checkMax();
        code[cIndex] = new InstOp(opr,p);
        return cIndex;
    }

    int genCodeR()					/*　ret命令語の生成　*/
    {
        if (code[cIndex].opCode == ret)
            return cIndex;			/*　直前がretなら生成せず　*/
        checkMax();
        code[cIndex] = new InstAddr(ret, new RelAddr(table.bLevel(), table.fPars()));
        return cIndex;
    }

    void checkMax()		/*　目的コードのインデックスの増加とチェック　*/
    {
        if (++cIndex < MAXCODE)
            return;
        source.errorF("too many code");
    }

    void backPatch(int i)		/*　命令語のバックパッチ（次の番地を）　*/
    {
        ((InstVal)code[i]).value = cIndex+1;
    }

    void listCode()			/*　命令語のリスティング　*/
    {
        int i;
        System.out.println("\n ******** code ********");
        for(i=0; i<=cIndex; i++){
            System.out.print(i + ": ");
            printCode(i);
        }
    }

    void printCode(int i)		/*　命令語の印字　*/
    {
        int flag = 1;
        switch(code[i].opCode){
            case lit: System.out.print("lit"); flag=1; break;
            case opr: System.out.print("opr"); flag=3; break;
            case lod: System.out.print("lod"); flag=2; break;
            case sto: System.out.print("sto"); flag=2; break;
            case cal: System.out.print("cal"); flag=2; break;
            case ret: System.out.print("ret"); flag=2; break;
            case ict: System.out.print("ict"); flag=1; break;
            case jmp: System.out.print("jmp"); flag=1; break;
            case jpc: System.out.print("jpc"); flag=1; break;
        }
        switch(flag){
            case 1:
                System.out.println("," + ((InstVal)code[i]).value);
                return;
            case 2:
                System.out.print("," + ((InstAddr)code[i]).addr.level);
                System.out.println("," + ((InstAddr)code[i]).addr.addr);
                return;
            case 3:
                switch(((InstOp)code[i]).optr){
                    case neg: System.out.print(",neg\n"); return;
                    case add: System.out.print(",add\n"); return;
                    case sub: System.out.print(",sub\n"); return;
                    case mul: System.out.print(",mul\n"); return;
                    case div: System.out.print(",div\n"); return;
                    case odd: System.out.print(",odd\n"); return;
                    case eq: System.out.print(",eq\n"); return;
                    case ls: System.out.print(",ls\n"); return;
                    case gr: System.out.print(",gr\n"); return;
                    case neq: System.out.print(",neq\n"); return;
                    case lseq: System.out.print(",lseq\n"); return;
                    case greq: System.out.print(",greq\n"); return;
                    case wrt: System.out.print(",wrt\n"); return;
                    case wrl: System.out.print(",wrl\n"); return;
                }
        }
    }

    void execute()			/*　目的コード（命令語）の実行　*/
    {
        int[] stack = new int[MAXMEM];		/*　実行時スタック　*/
        int[] display = new int[MAXLEVEL];	/*　現在見える各ブロックの先頭番地のディスプレイ　*/
        int pc, top, lev, temp;
        Inst i;					/*　実行する命令語　*/
        System.out.print("start execution\n");
        top = 0;  pc = 0;			/*　top:次にスタックに入れる場所、pc:命令語のカウンタ　*/
        stack[0] = 0;  stack[1] = 0; 	/*　stack[top]はcalleeで壊すディスプレイの退避場所　*/
        /*　stack[top+1]はcallerへの戻り番地　*/
        display[0] = 0;			/*　主ブロックの先頭番地は 0　*/

        if (trace) System.out.println("\n ******** trace ********" );

        do {
            if (trace) {
                int ctop = (top == 0)? 0 : top -1;
                System.out.println("\tstack[" + ctop + "] = " + stack[ctop]);
                System.out.print(pc + ":" );
                printCode(pc);
            }
            i = code[pc++];			/*　これから実行する命令語　*/
            switch(i.opCode){
                case lit: stack[top++] = ((InstVal)i).value;
                    break;
                case lod: stack[top++] = stack[display[((InstAddr)i).addr.level] + ((InstAddr)i).addr.addr];
                    break;
                case sto: stack[display[((InstAddr)i).addr.level] + ((InstAddr)i).addr.addr] = stack[--top];
                    break;
                case cal: lev = ((InstAddr)i).addr.level +1;		/*　 i.addr.levelはcalleeの名前のレベル　*/
                    /*　 calleeのブロックのレベルlevはそれに＋１したもの　*/
                    stack[top] = display[lev]; 	/*　display[lev]の退避　*/
                    stack[top+1] = pc; display[lev] = top;
                    /*　現在のtopがcalleeのブロックの先頭番地　*/
                    pc = ((InstAddr)i).addr.addr;
                    break;
                case ret: temp = stack[--top];		/*　スタックのトップにあるものが返す値　*/
                    top = display[((InstAddr)i).addr.level];  	/*　topを呼ばれたときの値に戻す　*/
                    display[((InstAddr)i).addr.level] = stack[top];		/* 壊したディスプレイの回復 */
                    pc = stack[top+1];
                    top -= ((InstAddr)i).addr.addr;		/*　実引数の分だけトップを戻す　*/
                    stack[top++] = temp;		/*　返す値をスタックのトップへ　*/
                    break;
                case ict: top += ((InstVal)i).value;
                    if (top >= MAXMEM-MAXREG)
                        source.errorF("stack overflow");
                    break;
                case jmp: pc = ((InstVal)i).value; break;
                case jpc: if (stack[--top] == 0)
                    pc = ((InstVal)i).value;
                    break;
                case opr:
                    switch(((InstOp)i).optr){
                        case neg: stack[top-1] = -stack[top-1]; continue;
                        case add: --top;  stack[top-1] += stack[top]; continue;
                        case sub: --top; stack[top-1] -= stack[top]; continue;
                        case mul: --top;  stack[top-1] *= stack[top];  continue;
                        case div: --top;  stack[top-1] /= stack[top]; continue;
                        case odd: stack[top-1] = stack[top-1] & 1; continue;
                        case eq: --top;  stack[top-1] = (stack[top-1] == stack[top]? 1:0); continue;
                        case ls: --top;  stack[top-1] = (stack[top-1] < stack[top]? 1:0); continue;
                        case gr: --top;  stack[top-1] = (stack[top-1] > stack[top]? 1:0); continue;
                        case neq: --top;  stack[top-1] = (stack[top-1] != stack[top]? 1:0); continue;
                        case lseq: --top;  stack[top-1] = (stack[top-1] <= stack[top]? 1:0); continue;
                        case greq: --top;  stack[top-1] = (stack[top-1] >= stack[top]? 1:0); continue;
                        case wrt: System.out.print(stack[--top] + " "); continue;
                        case wrl: System.out.print("\n"); continue;
                    }
            }
        } while (pc != 0);
    }
}