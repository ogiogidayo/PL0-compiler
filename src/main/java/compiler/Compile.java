package compiler;

public class Compile {
    GetSource source;
    Table table;
    CodeGen codeGen;
    boolean objCode;

    static final int MINERROR = 3;
    static final int FIRSTADDR = 2;
    static Token token;				/*　次のトークンを入れておく　*/

    public Compile(GetSource s, Table t, CodeGen c, boolean o){
        source = s;
        table = t;
        codeGen = c;  objCode = o;
    }


    public boolean compile(){
        int i;
        System.out.println("start compilation" );
        source.initSource();				/*　getSourceの初期設定　*/
        token = source.nextToken();			/*　最初のトークン　*/
        table.blockBegin(FIRSTADDR);		/*　これ以後の宣言は新しいブロックのもの　*/
        block(0);					/*　0 はダミー（主ブロックの関数名はない）　*/
        source.finalSource();
        i = source.errorN();				/*　エラーメッセージの個数　*/
        if (i!=0)
            System.out.println(i + " errors");
        if (objCode)
            codeGen.listCode();				/*　目的コードのリスト　*/
        return i<MINERROR;		/*　エラーメッセージの個数が少ないかどうかの判定　*/
    }

    void block(int pIndex) {
        /*　pIndex はこのブロックの関数名のインデックス　*/

        int backP;
        backP = codeGen.genCodeV(CodeGen.jmp, 0);		/*　内部関数を飛び越す命令、後でバックパッチ　*/
        while (true) {				/*　宣言部のコンパイルを繰り返す　*/
            switch (token.kind){
                case GetSource.Const:			/*　定数宣言部のコンパイル　*/
                    token = source.nextToken();
                    constDecl(); continue;
                case GetSource.Var:				/*　変数宣言部のコンパイル　*/
                    token = source.nextToken();
                    varDecl(); continue;
                case GetSource.Func:				/*　関数宣言部のコンパイル　*/
                    token = source.nextToken();
                    funcDecl(); continue;
                default:				/*　それ以外なら宣言部は終わり　*/
                    break;
            }
            break;
        }
        codeGen.backPatch(backP);			/*　内部関数を飛び越す命令にパッチ　*/
        if(pIndex != 0)
            table.changeV(pIndex, codeGen.nextCode());	/*　この関数の開始番地を修正　*/
        codeGen.genCodeV(CodeGen.ict, table.frameL());		/*　このブロックの実行時の必要記憶域をとる命令　*/
        statement();				/*　このブロックの主文　*/
        codeGen.genCodeR();				/*　リターン命令　*/
        table.blockEnd();				/*　ブロックが終ったこと */
    }

    void constDecl()      /*　定数宣言のコンパイル　*/
    {
        Token temp;
        while( true ){
            if (token.kind==GetSource.Id){
                source.setIdKind(GetSource.constId);			/*　印字のための情報のセット　*/
                temp = token; 					/*　名前を入れておく　*/
                token = source.checkGet(source.nextToken(), GetSource.Equal);		/*　名前の次は"="のはず　*/
                if (token.kind==GetSource.Num)
                    table.enterTconst(temp.id, token.value);	/*　定数名と値をテーブルに　*/
                else
                    source.errorType("number");
                token = source.nextToken();
            }else
                source.errorMissingId();
            if (token.kind!=GetSource.Comma){		/*　次がコンマなら定数宣言が続く　*/
                if (token.kind==GetSource.Id){		/*　次が名前ならコンマを忘れたことにする　*/
                    source.errorInsert(GetSource.Comma);
                    continue;
                }else
                    break;
            }
            token = source.nextToken();
        }
        token = source.checkGet(token, GetSource.Semicolon);		/*　最後は";"のはず　*/
    }

    void varDecl() {	/*　変数宣言のコンパイル　*/
        while(true){
            if (token.kind==GetSource.Id){
                source.setIdKind(GetSource.varId);		/*　印字のための情報のセット　*/
                table.enterTvar(token.id);		/*　変数名をテーブルに、番地はtableが決める　*/
                token = source.nextToken();
            }else
                source.errorMissingId();
            if (token.kind!=GetSource.Comma){		/*　次がコンマなら変数宣言が続く　*/
                if (token.kind==GetSource.Id){		/*　次が名前ならコンマを忘れたことにする　*/
                    source.errorInsert(GetSource.Comma);
                    continue;
                }else
                    break;
            }
            token = source.nextToken();
        }
        token = source.checkGet(token, GetSource.Semicolon);		/*　最後は";"のはず　*/
    }

    void funcDecl() {   /*　関数宣言のコンパイル　*/
        int fIndex;
        if (token.kind==GetSource.Id){
            source.setIdKind(GetSource.funcId);				/*　印字のための情報のセット　*/
            fIndex = table.enterTfunc(token.id, codeGen.nextCode());		/*　関数名をテーブルに登録　*/
            /*　その先頭番地は、まず、次のコードの番地nextCode()とする　*/
            token = source.checkGet(source.nextToken(), GetSource.Lparen);
            table.blockBegin(FIRSTADDR);	/*　パラメタ名のレベルは関数のブロックと同じ　*/
            while(true){
                if (token.kind==GetSource.Id){			/*　パラメタ名がある場合　*/
                    source.setIdKind(GetSource.parId);		/*　印字のための情報のセット　*/
                    table.enterTpar(token.id);		/*　パラメタ名をテーブルに登録　*/
                    token = source.nextToken();
                }else
                    break;
                if (token.kind!=GetSource.Comma){		/*　次がコンマならパラメタ名が続く　*/
                    if (token.kind==GetSource.Id){		/*　次が名前ならコンマを忘れたことに　*/
                        source.errorInsert(GetSource.Comma);
                        continue;
                    }else
                        break;
                }
                token = source.nextToken();
            }
            token = source.checkGet(token, GetSource.Rparen);		/*　最後は")"のはず　*/
            table.endpar();				/*　パラメタ部が終わったことをテーブルに連絡　*/
            if (token.kind==GetSource.Semicolon){
                source.errorDelete();
                token = source.nextToken();
            }
            block(fIndex);	/*　ブロックのコンパイル、その関数名のインデックスを渡す　*/
            token = source.checkGet(token, GetSource.Semicolon);		/*　最後は";"のはず　*/
        } else
            source.errorMissingId();			/*　関数名がない　*/
    }

    void statement()			/*　文のコンパイル　*/
    {
        int tIndex;
        int k;
        int backP, backP2;				/*　バックパッチ用　*/

        while(true) {
            switch (token.kind) {
                case GetSource.Id:					/*　代入文のコンパイル　*/
                    tIndex = table.searchT(token.id, GetSource.varId);	/*　左辺の変数のインデックス　*/
                    source.setIdKind(k=table.kindT(tIndex));			/*　印字のための情報のセット　*/
                    if (k != GetSource.varId && k != GetSource.parId) 		/*　変数名かパラメタ名のはず　*/
                        source.errorType("var/par");
                    token = source.checkGet(source.nextToken(), GetSource.Assign);			/*　":="のはず　*/
                    expression();					/*　式のコンパイル　*/
                    codeGen.genCodeT(CodeGen.sto, tIndex);				/*　左辺への代入命令　*/
                    return;
                case GetSource.If:					/*　if文のコンパイル　*/
                    token = source.nextToken();
                    condition();					/*　条件式のコンパイル　*/
                    token = source.checkGet(token, GetSource.Then);		/*　"then"のはず　*/
                    backP = codeGen.genCodeV(CodeGen.jpc, 0);			/*　jpc命令　*/
                    statement();					/*　文のコンパイル　*/
                    codeGen.backPatch(backP);				/*　上のjpc命令にバックパッチ　*/
                    return;
                case GetSource.Ret:					/*　return文のコンパイル　*/
                    token = source.nextToken();
                    expression();					/*　式のコンパイル　*/
                    codeGen.genCodeR();					/*　ret命令　*/
                    return;
                case GetSource.Begin:				/*　begin . . end文のコンパイル　*/
                    token = source.nextToken();
                    while(true){
                        statement();				/*　文のコンパイル　*/
                        while(true){
                            if (token.kind==GetSource.Semicolon){		/*　次が";"なら文が続く　*/
                                token = source.nextToken();
                                break;
                            }
                            if (token.kind==GetSource.End){			/*　次がendなら終り　*/
                                token = source.nextToken();
                                return;
                            }
                            if (isStBeginKey(token)){		/*　次が文の先頭記号なら　*/
                                source.errorInsert(GetSource.Semicolon);	/*　";"を忘れたことにする　*/
                                break;
                            }
                            source.errorDelete();	/*　それ以外ならエラーとして読み捨てる　*/
                            token = source.nextToken();
                        }
                    }
                case GetSource.While:				/*　while文のコンパイル　*/
                    token = source.nextToken();
                    backP2 = codeGen.nextCode();			/*　while文の最後のjmp命令の飛び先　*/
                    condition();				/*　条件式のコンパイル　*/
                    token = source.checkGet(token, GetSource.Do);	/*　"do"のはず　*/
                    backP = codeGen.genCodeV(CodeGen.jpc, 0);		/*　条件式が偽のとき飛び出すjpc命令　*/
                    statement();				/*　文のコンパイル　*/
                    codeGen.genCodeV(CodeGen.jmp, backP2);		/*　while文の先頭へのジャンプ命令　*/
                    codeGen.backPatch(backP);	/*　偽のとき飛び出すjpc命令へのバックパッチ　*/
                    return;
                case GetSource.Write:			/*　write文のコンパイル　*/
                    token = source.nextToken();
                    expression();				/*　式のコンパイル　*/
                    codeGen.genCodeO(CodeGen.wrt);				/*　その値を出力するwrt命令　*/
                    return;
                case GetSource.Writeln:			/*　writeln文のコンパイル　*/
                    token = source.nextToken();
                    codeGen.genCodeO(CodeGen.wrl);				/*　改行を出力するwrl命令　*/
                    return;
                case GetSource.End: case GetSource.Semicolon:			/*　空文を読んだことにして終り　*/
                    return;
                default:				/*　文の先頭のキーまで読み捨てる　*/
                    source.errorDelete();				/*　今読んだトークンを読み捨てる　*/
                    token = source.nextToken();
                    continue;
            }
        }
    }

    boolean isStBeginKey(Token t)
    {
        switch(t.kind){
            case GetSource.If: case GetSource.Begin: case GetSource.Ret:
            case GetSource.While: case GetSource.Write: case GetSource.Writeln:
                return true;
            default:
                return false;
        }
    }

    void expression()
        /*　式のコンパイル　*/
    {
        int k;
        k = token.kind;
        if (k==GetSource.Plus || k==GetSource.Minus){
            token = source.nextToken();
            term();
            if (k==GetSource.Minus)
                codeGen.genCodeO(CodeGen.neg);
        }else
            term();
        k = token.kind;
        while (k==GetSource.Plus || k==GetSource.Minus){
            token = source.nextToken();
            term();
            if (k==GetSource.Minus)
                codeGen.genCodeO(CodeGen.sub);
            else
                codeGen.genCodeO(CodeGen.add);
            k = token.kind;
        }
    }

    void term() {  /*　式の項のコンパイル　*/
        int k;
        factor();
        k = token.kind;
        while (k==GetSource.Mult || k==GetSource.Div){
            token = source.nextToken();
            factor();
            if (k==GetSource.Mult)
                codeGen.genCodeO(CodeGen.mul);
            else
                codeGen.genCodeO(CodeGen.div);
            k = token.kind;
        }
    }

    void factor()
        /*　式の因子のコンパイル　*/
    {
        int tIndex, i;
        int k;
        if (token.kind==GetSource.Id){
            tIndex = table.searchT(token.id, GetSource.varId);
            source.setIdKind(k=table.kindT(tIndex));			/*　印字のための情報のセット　*/
            switch (k) {
                case GetSource.varId: case GetSource.parId:			/*　変数名かパラメタ名　*/
                    codeGen.genCodeT(CodeGen.lod, tIndex);
                    token = source.nextToken(); break;
                case GetSource.constId:					/*　定数名　*/
                    codeGen.genCodeV(CodeGen.lit, table.val(tIndex));
                    token = source.nextToken(); break;
                case GetSource.funcId:					/*　関数呼び出し　*/
                    token = source.nextToken();
                    if (token.kind==GetSource.Lparen){
                        i=0; 					/*　iは実引数の個数　*/
                        token = source.nextToken();
                        if (token.kind != GetSource.Rparen) {
                            for (; ; ) {
                                expression(); i++;	/*　実引数のコンパイル　*/
                                if (token.kind==GetSource.Comma){	/* 次がコンマなら実引数が続く */
                                    token = source.nextToken();
                                    continue;
                                }
                                token = source.checkGet(token, GetSource.Rparen);
                                break;
                            }
                        } else
                            token = source.nextToken();
                        if (table.pars(tIndex) != i)
                            source.errorMessage("\\#par");	/*　pars(tIndex)は仮引数の個数　*/
                    }else{
                        source.errorInsert(GetSource.Lparen);
                        source.errorInsert(GetSource.Rparen);
                    }
                    codeGen.genCodeT(CodeGen.cal, tIndex);				/*　call命令　*/
                    break;
            }
        }else if (token.kind==GetSource.Num){			/*　定数　*/
            codeGen.genCodeV(CodeGen.lit, token.value);
            token = source.nextToken();
        }else if (token.kind==GetSource.Lparen){			/*　「(」「因子」「)」　*/
            token = source.nextToken();
            expression();
            token = source.checkGet(token, GetSource.Rparen);
        }
        switch (token.kind){					/*　因子の後がまた因子ならエラー　*/
            case GetSource.Id: case GetSource.Num: case GetSource.Lparen:
                source.errorMissingOp();
                factor();
            default:
                return;
        }
    }

    void condition() { /*　条件式のコンパイル　*/
        int k;
        if( token.kind == GetSource.Odd){
            token = source.nextToken();
            expression();
            codeGen.genCodeO(CodeGen.odd);
        }else{
            expression();
            k = token.kind;
            switch(k){
                case GetSource.Equal: case GetSource.Lss: case GetSource.Gtr:
                case GetSource.NotEq: case GetSource.LssEq: case GetSource.GtrEq:
                    break;
                default:
                    source.errorType("rel-op");
                    break;
            }
            token = source.nextToken();
            expression();
            switch(k){
                case GetSource.Equal:	codeGen.genCodeO(CodeGen.eq); break;
                case GetSource.Lss:		codeGen.genCodeO(CodeGen.ls); break;
                case GetSource.Gtr:		codeGen.genCodeO(CodeGen.gr); break;
                case GetSource.NotEq:	codeGen.genCodeO(CodeGen.neq); break;
                case GetSource.LssEq:	codeGen.genCodeO(CodeGen.lseq); break;
                case GetSource.GtrEq:	codeGen.genCodeO(CodeGen.greq); break;
            }
        }
    }
}
