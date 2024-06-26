package compiler;

import java.lang.*;
public class PL0 {

    public static void main(String[] args) {
        boolean symTable = false;  /* trueなら各ブロックの記号表を印字 */
        boolean objCode = false;   /* trueなら目的コードを印字 */
        boolean trace = false;     /* trueなら実行のトレース情報を印字 */
        boolean byteCode = false;  /* trueなら目的コードをバイトコードとする */
        String s = args[0];
        if (s.charAt(0) == '-') {
            for (int j = 1; j < s.length(); j++) {
                switch (s.charAt(j)) {
                    case 's': symTable = true; break;
                    case 'o': objCode = true; break;
                    case 't': trace = true; break;
                    case 'b': byteCode = true; break;
                }
            }
            s = args[1];
        }

        GetSource source =  new GetSource();
        Table table = new Table(source, symTable);
        source.set(table);
        CodeGen codeGen;
        if (byteCode)
            codeGen = new CodeGenB(source, table, trace);
        else
            codeGen = new CodeGen(source, table, trace);
        Compile compiler = new Compile(source, table, codeGen, objCode);
        if (!source.openSource(s))
            return;
        if (compiler.compile())
            codeGen.execute();
        source.closeSource();
    }
}
