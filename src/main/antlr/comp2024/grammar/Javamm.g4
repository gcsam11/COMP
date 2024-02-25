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
    : IMPORT ID ( DOT ID )* SEMI #ImportDecl
    ;

classDeclaration
    : CLASS ID ( EXTENDS ID )? LCURLY ( varDeclaration )* ( methodDeclaration )* RCURLY #ClassDecl
    ;

varDeclaration
    : type ID SEMI #VarDecl
    ;

methodDeclaration
    : (PUBLIC)? type ID LPAREN ( type ID ( COMMA type ID )* )? RPAREN LCURLY ( varDeclaration)* ( stmt )* RETURN expr SEMI RCURLY #MethodReturnDecl
    | (PUBLIC)? STATIC VOID MAIN LPAREN STRING LSQUARE RSQUARE ID RPAREN LCURLY ( varDeclaration )* ( stmt )* RCURLY #MainMethodDecl
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
    | ID EQUALS expr SEMI #Assign
    | ID LSQUARE expr RSQUARE EQUALS expr SEMI #ArrayAssign
    ;

expr
    : expr (DOT LENGTH | DOT ID LPAREN ( expr ( COMMA expr )* )? RPAREN | LSQUARE expr RSQUARE) #MemberOrArrayAccessOp
    | LPAREN expr RPAREN #ParenOp
    | NEG expr #UnaryOp
    | (NEW INT LSQUARE expr RSQUARE | NEW ID LPAREN RPAREN) #NewOp
    | expr (MUL | DIV) expr #BinaryOp
    | expr (ADD | SUB) expr #BinaryOp
    | expr (LT) expr #BinaryOp
    | expr (AND) expr #BinaryOp
    | LSQUARE ( expr ( COMMA expr )* )? RSQUARE #ArrayCreation
    | INT #Int
    | TRUE #True
    | FALSE #False
    | ID #Identifier
    | THIS #This
    | NULL #Null
    | INTEGER #Integer
    ;

anIntArray
    : INT LSQUARE ( INTEGER ( COMMA INTEGER )* )? RSQUARE
    ;
