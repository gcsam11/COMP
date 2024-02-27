grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

EQUALS : '=';
SEMI : ';' ;
LCURLY : '{' ;
RCURLY : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
MUL : '*' ;
ADD : '+' ;
SUB : '-';
DIV : '/';
DOT : '.';
ELLIPSIS : '...';
LSQUARE : '[';
RSQUARE : ']';
COMMA : ',';
AND : '&&';
LT : '<';
NEG : '!';

CLASS : 'class' ;
INT : 'int' ;
PUBLIC : 'public' ;
RETURN : 'return' ;
TRUE : 'true' ;
FALSE : 'false' ;
THIS : 'this' ;
NULL : 'null' ;
IF : 'if' ;
ELSE : 'else' ;
WHILE : 'while' ;
NEW : 'new' ;
BOOLEAN : 'boolean' ;
STATIC : 'static' ;
VOID : 'void' ;
MAIN : 'main' ;
STRING : 'String' ;
IMPORT : 'import';
EXTENDS : 'extends';
LENGTH : 'length';

INTEGER : '0' | [1-9][0-9]* ;
ID : [a-zA-Z_$] [a-zA-Z0-9_$]*;

ML_COMMENT : '/*' .*? '*/' -> skip ;
EOL_COMMENT : '//' ~[\r\n]* -> skip ;

WS : [ \t\n\r\f]+ -> skip ;

program
    : (importDeclaration)* classDeclaration EOF
    ;

importDeclaration
    : op=IMPORT value+=ID ( DOT value+=ID )* SEMI #ImportDecl
    ;

classDeclaration
    : CLASS value+=ID ( EXTENDS value+=ID )? LCURLY ( varDeclaration )* ( methodDeclaration )* RCURLY #ClassDecl
    ;

varDeclaration
    : type value=ID SEMI #VarDecl
    ;

methodDeclaration
    : (PUBLIC)? type var+=ID LPAREN ( type var+=ID ( COMMA type var+=ID )* )? RPAREN LCURLY ( varDeclaration)* ( stmt )* RETURN expr SEMI RCURLY #MethodReturnDecl
    | (PUBLIC)? STATIC VOID MAIN LPAREN STRING LSQUARE RSQUARE var=ID RPAREN LCURLY ( varDeclaration )* ( stmt )* RCURLY #MainMethodDecl
    ;

type
    : INT LSQUARE RSQUARE #IntArrayType
    | INT ELLIPSIS #IntEllipsisType
    | BOOLEAN #BooleanType
    | INT #IntType
    | STRING #StringType
    | ID #IdentifierType
    | anIntArray #AnIntArrayType
    ;

stmt
    : LCURLY ( stmt )* RCURLY #Block
    | IF LPAREN expr RPAREN stmt ELSE stmt #IfElse
    | WHILE LPAREN expr RPAREN stmt #While
    | expr SEMI #ExprStmt
    | var=ID EQUALS expr SEMI #Assign
    | var=ID LSQUARE expr RSQUARE EQUALS expr SEMI #ArrayAssign
    ;
expr
    : expr (op+=DOT func=LENGTH | op+=DOT value=ID LPAREN ( expr ( COMMA expr )* )? RPAREN | LSQUARE expr RSQUARE) #MemberOrArrayAccessOp
    | op=LPAREN expr RPAREN #ParenOp
    | op=NEG expr #UnaryOp
    | (op+=NEW value+=INT LSQUARE expr RSQUARE | op+=NEW value+=ID LPAREN RPAREN) #NewOp
    | expr op=(MUL | DIV) expr #BinaryOp
    | expr op=(ADD | SUB) expr #BinaryOp
    | expr op=LT expr #BinaryOp
    | expr op=AND expr #BinaryOp
    | LSQUARE ( expr ( COMMA expr )* )? RSQUARE #ArrayCreation
    | value=INT #Int
    | value=TRUE #True
    | value=FALSE #False
    | value=ID #Identifier
    | value=THIS #This
    | value=NULL #Null
    | value=INTEGER #Integer
    ;

anIntArray
    : INT LSQUARE ( INTEGER ( COMMA INTEGER )* )? RSQUARE
    ;
