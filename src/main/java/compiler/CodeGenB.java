package compiler;

public class CodeGenB extends CodeGen {

    public CodeGenB(GetSource s, Table t, boolean trace) {
        super(s, t, trace);
    }

    static final int MAXCODE = CodeGen.MAXCODE * 3; 	/*　目的コードの最大長さ　*/
    byte[] bCode = new byte[MAXCODE];		/*　目的コードが入る　*/
    int prevCIndex = 0; 	/*　最後に作った命令のインデックス　*/
    private byte[] bytes = new byte[4];		/*　整数とバイト列の変換用　*/

    private void bytesFromInt(int v) {	/*　intからバイト列への変換　*/
        int i, index = 0;
        for (i = 0; i < 4; i++)
            bytes[i] = 0;
        for ( i = v; i != 0; i >>>= 8 ) {
            int s = i & 0x000000ff;
            if (s > 127) s -= 256;
            bytes[index++] = (byte)s;
        }
    }

    private int intFromBytes(byte[] b, int index) { /*　4バイトからintへの変換　*/
        int r = ((int)b[index+3]) << 24;
        for (int i = 2; i >= 0; i--)
            r |= (((int)b[index+i]) & 0x000000ff ) << i*8 ;
        return r;
    }

    private short shortFromBytes(byte[] b, int index) { /*　2バイトからshortへの変換　*/
        int r = ((short)b[index+1]) << 8;
        r |= (((short)b[index]) & 0x00ff ) ;
        return (short)r;
    }

    int genCodeV(int op, int v)		/*　命令語の生成、アドレス部にv　*/
    {
        checkMax();
        bCode[cIndex] = (byte)op;
        bytesFromInt(v);
        if (op != lit) {
            bCode[++cIndex] = bytes[0]; 	/*　jmp, jpc, ict のアドレス部は2バイト　*/
            bCode[++cIndex] = bytes[1];
        }
        else
            for (int i = 0; i <= 3; i++) 	/*　lit のアドレス部は2バイト　*/
                bCode[++cIndex] = bytes[i];
        return cIndex;
    }


    int genCodeT(int op, int ti)		/*　命令語の生成、アドレスは名前表から　*/
    {
        checkMax();
        bCode[cIndex] = (byte)op;
        RelAddr ra = table.relAddr(ti);
        bCode[++cIndex] = (byte)ra.level;  /*　レベル部は１バイト　*/
        bytesFromInt(ra.addr);
        bCode[++cIndex] = bytes[0];  		/*　アドレス部は2バイト　*/
        bCode[++cIndex] = bytes[1];
        return cIndex;
    }

    int genCodeO(int p)			/*　命令語の生成、演算命令は1バイト　*/
    {
        checkMax();
        bCode[cIndex] = (byte)p;
        return cIndex;
    }

    int genCodeR()		/*　ret命令語の生成、アドレスはレベルと引数(各1バイト）*/
    {
        if (bCode[prevCIndex] == ret)
            return cIndex;			/*　直前がretなら生成せず　*/
        checkMax();
        bCode[cIndex] = (byte)ret;
        bCode[++cIndex] = (byte)table.bLevel();
        bCode[++cIndex] = (byte)table.fPars();
        return cIndex;
    }

    void checkMax()		/*　目的コードのインデックスの増加とチェック　*/
    {
        prevCIndex = ++cIndex;
        if (cIndex < MAXCODE - 5)
            return;
        source.errorF("too many code");
    }

    void backPatch(int i)		/*　命令語のバックパッチ（次の番地を）　*/
    {
        bytesFromInt(cIndex+1);
        bCode[i-1] = bytes[0];
        bCode[i] = bytes[1];
    }

    void listCode()			/*　命令語のリスティング　*/
    {
        int i;
        System.out.println("\n ******** byte code ********");
        for(i=0; i<=cIndex; ){
            System.out.print(i + ": ");
            i = printBCode(i);
        }
    }

    int printBCode(int i)		/*　命令語の印字　*/
    {
        int flag = 1;
        switch(bCode[i]){
            case lit: System.out.print("lit"); flag=1; break;
            case lod: System.out.print("lod"); flag=2; break;
            case sto: System.out.print("sto"); flag=2; break;
            case cal: System.out.print("cal"); flag=2; break;
            case ret: System.out.print("ret"); flag=4; break;
            case ict: System.out.print("ict"); flag=3; break;
            case jmp: System.out.print("jmp"); flag=3; break;
            case jpc: System.out.print("jpc"); flag=3; break;
            case neg: System.out.print("neg\n"); return i+1;
            case add: System.out.print("add\n"); return i+1;
            case sub: System.out.print("sub\n"); return i+1;
            case mul: System.out.print("mul\n"); return i+1;
            case div: System.out.print("div\n"); return i+1;
            case odd: System.out.print("odd\n"); return i+1;
            case eq: System.out.print("eq\n"); return i+1;
            case ls: System.out.print("ls\n"); return i+1;
            case gr: System.out.print("gr\n"); return i+1;
            case neq: System.out.print("neq\n"); return i+1;
            case lseq: System.out.print("lseq\n"); return i+1;
            case greq: System.out.print("greq\n"); return i+1;
            case wrt: System.out.print("wrt\n"); return i+1;
            case wrl: System.out.print("wrl\n"); return i+1;
        }
        switch(flag){
            case 1:
                int v = intFromBytes(bCode, i+1);
                System.out.println("," + v);
                return i+5;
            case 2:
                System.out.print("," + bCode[i+1]);
                short s = shortFromBytes(bCode, i+2);
                System.out.println("," + s);
                return i+4;
            case 3:
                s = shortFromBytes(bCode, i+1);
                System.out.println("," + s);
                return i+3;
            case 4:
                System.out.print("," + bCode[i+1]);
                System.out.println("," + bCode[i+2]);
                return i+3;
            default:
                System.out.println("unknown instruction");
                return i+1;
        }
    }

    void execute()			/*　目的コード（命令語）の実行　*/
    {
        System.out.print("start execution:\n");
        int[] stack = new int[MAXMEM];		/*　実行時スタック　*/
        int[] display = new int[MAXLEVEL];	/*　現在見える各ブロックの先頭番地のディスプレイ　*/
        int pc, top, lev, temp;
        byte i;					/*　実行する命令　*/
        top = 0;  pc = 0;			/*　top:次にスタックに入れる場所、pc:命令語のカウンタ　*/
        stack[0] = 0;  stack[1] = 0; 	/*　stack[top]はcalleeで壊すディスプレイの退避場所　*/
        /*　stack[top+1]はcallerへの戻り番地　*/
        display[0] = 0;			/*　主ブロックの先頭番地は 0　*/
//	int tCount = 0;          /*  トレースする命令の数を制限するとき使う */
        if (trace)  System.out.println("\n\t ******** trace ********" );

        do {		/*  命令実行のループ */
            if (trace) {
                int ctop = (top == 0)? 0 : top -1;
                System.out.println("\t\tstack[" + ctop + "] = " + stack[ctop]);
                System.out.print("\t" + pc + ":" );
                printBCode(pc);
//        if (++tCount >= 200) return;    /* トレースする命令の数を制限するとき使う */
            }
            i = bCode[pc++];			/*　これから実行する命令語　*/
            switch(i){
                case lit: stack[top++] = intFromBytes(bCode, pc);
                    pc += 4; break;
                case lod: stack[top++] = stack[display[bCode[pc]] + shortFromBytes(bCode, pc+1)];
                    pc += 3; break;
                case sto: stack[display[bCode[pc]] + shortFromBytes(bCode, pc+1)] = stack[--top];
                    pc += 3; break;
                case cal: lev = bCode[pc] +1;		/*　 bCode[pc]はcalleeの名前のレベル　*/
                    /*　 calleeのブロックのレベルlevはそれに＋１したもの　*/
                    stack[top] = display[lev]; 	/*　display[lev]の退避　*/
                    stack[top+1] = pc+3;    /*　戻り番地　*/
                    display[lev] = top;      /*　現在のtopがcalleeのブロックの先頭番地　*/
                    pc = shortFromBytes(bCode, pc+1);
                    break;
                case ret: temp = stack[--top];		/*　スタックのトップにあるものが返す値　*/
                    top = display[bCode[pc]];  	/*　topを呼ばれたときの値に戻す　*/
                    display[bCode[pc]] = stack[top];		/* 壊したディスプレイの回復 */
                    int params = bCode[pc+1];		/*　実引数の数　*/
                    pc = stack[top+1];
                    top -= params;		/*　実引数の分だけトップを戻す　*/
                    stack[top++] = temp;		/*　返す値をスタックのトップへ　*/
                    break;
                case ict: top += shortFromBytes(bCode, pc);
                    if (top >= MAXMEM-MAXREG)
                        source.errorF("stack overflow");
                    pc += 2; break;
                case jmp: pc = shortFromBytes(bCode, pc); break;
                case jpc: if (stack[--top] == 0)
                    pc = shortFromBytes(bCode, pc);
                else pc += 2;
                    break;
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
        } while (pc != 0);
    }
}