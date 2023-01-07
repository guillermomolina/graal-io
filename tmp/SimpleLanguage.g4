grammar SimpleLanguage;

// parser

simplelanguage: function function* EOF;

function:
	'function' name = IDENTIFIER s = LPAREN (parameterList)? RPAREN body = block;

parameterList: IDENTIFIER (COMMA IDENTIFIER)*;

block: s = '{' ( statement)* e = '}';

statement:
	whileStatement
	| b = 'break' ';'
	| c = 'continue' ';'
	| ifStatement
	| returnStatement
	| expression ';'
	| d = 'debugger' ';';

whileStatement:
	'while' LPAREN condition = expression RPAREN body = block;

ifStatement:
	'if' LPAREN condition = expression RPAREN then = block (
		'else' block
	)?;

returnStatement: 'return' ( expression)? ';';

expression: logicTerm ( op = '||' logicTerm)*;

logicTerm: logicFactor ( op = '&&' logicFactor)*;

logicFactor:
	arithmetic (
		op = ('<' | '<=' | '>' | '>=' | '==' | '!=') arithmetic
	)?;

arithmetic: term ( op = ('+' | '-') term)*;

term: factor ( op = ('*' | '/') factor)*;

factor:
	literal = IDENTIFIER memberExpression?
	| stringLiteral = STRING_LITERAL
	| numericLiteral = NUMERIC_LITERAL
	| s = LPAREN expr = expression e = RPAREN;

memberExpression:
	(
		prnt = LPAREN (expression ( COMMA expression)*)? e = RPAREN
		| eq = '=' expression
		| dot = '.' literal = IDENTIFIER
		| brkt = '[' expression ']'
	) (memberExpression)?;

// lexer

LPAREN: '(';
RPAREN: ')';
COMMA: ',';

WS: [ \t\r\n\u000C]+ -> skip;
COMMENT: '/*' .*? '*/' -> skip;
LINE_COMMENT: '//' ~[\r\n]* -> skip;

fragment LETTER: [A-Z] | [a-z] | '_' | '$';
fragment NON_ZERO_DIGIT: [1-9];
fragment DIGIT: [0-9];
fragment HEX_DIGIT: [0-9] | [a-f] | [A-F];
fragment OCT_DIGIT: [0-7];
fragment BINARY_DIGIT: '0' | '1';
fragment TAB: '\t';
fragment STRING_CHAR: ~('"' | '\r' | '\n');

IDENTIFIER: LETTER (LETTER | DIGIT)*;
STRING_LITERAL: '"' STRING_CHAR* '"';
NUMERIC_LITERAL: '0' | NON_ZERO_DIGIT DIGIT*;
