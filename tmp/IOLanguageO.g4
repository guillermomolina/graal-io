grammar IOLanguageO;

iolanguage: expression EOF;

expression: (message | terminator)+;

message: EOL? (symbol arguments? | arguments);
arguments: OPEN (argument (COMMA argument)*)? CLOSE;
argument: EOL? expression EOL?;

symbol: 
    id=IDENTIFIER 
    | num=number 
    | op=OPERATOR
    | str=STRING;

number: 
    DECIMAL_INTEGER 
    | HEX_NUMBER 
    | OCT_NUMBER 
    | BIN_NUMBER 
    | FLOAT_NUMBER;

terminator: ';' | EOL;

LINE_JOINS: '\\' [ \t]* RN -> channel(HIDDEN);
EOL: RN;

WS: [ \t\u000C]+ -> channel(HIDDEN);
COMMENT: '/*' .*? '*/' -> channel(HIDDEN);
LINE_COMMENT: ('//' | '#') ~[\r\n]* -> channel(HIDDEN);

fragment LETTER: [A-Z] | [a-z];
fragment NON_ZERO_DIGIT: [1-9];
fragment DIGIT: [0-9];
fragment HEX_DIGIT: [0-9] | [a-f] | [A-F];
fragment OCT_DIGIT: [0-7];
fragment BIN_DIGIT: [01];
fragment TAB: '\t';
fragment STRING_CHAR: ~('"' | '\r' | '\n');
fragment RN: '\r'? '\n';

fragment EXPONENT_OR_POINT_FLOAT
    : ([0-9]+ | POINT_FLOAT) [eE] [+-]? [0-9]+
    | POINT_FLOAT
    ;

fragment POINT_FLOAT
    : [0-9]* '.' [0-9]+
    | [0-9]+ '.'
    ;

IDENTIFIER: (LETTER | '_') (LETTER | '_' | DIGIT)*;

OPERATOR:  (':' | '.' | '\'' | '~' | '!' | '@' | '$' | 
    '%' | '^' | '&' | '*' | '-' | '+' | '/' | '=' | '{' | '}' | 
    '[' | ']' | '|' | '\\' | '<' | '>' | '?')+;

COMMA: ',';
OPEN: '(' | '[' | '{';
CLOSE:')' | ']' | '}';
STRING: MONOQUOTE | TRIQUOTE;
MONOQUOTE: '"' STRING_CHAR* '"';
TRIQUOTE: '"""' STRING_CHAR* '"""';

DECIMAL_INTEGER: [1-9] [0-9]*
                | '0'+
                ;
HEX_NUMBER: '0' [xX] HEX_DIGIT+;
OCT_NUMBER: '0' [oO] OCT_DIGIT+;
BIN_NUMBER: '0' [bB] BIN_DIGIT+;
FLOAT_NUMBER: EXPONENT_OR_POINT_FLOAT;