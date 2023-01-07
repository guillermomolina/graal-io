# GRAAL-IO

This is a GraalVM implementation of [Io](http://iolanguage.org/). Much of the hard work has been done, but hard work still remains.

[This implementation does not always behave identically to the original.](#the-io-language)

# The Io Language

_Note: This document is intended to be used as a reference for setting up and configuring Io. For a guide on how to use the language itself, please visit the website at <http://iolanguage.org/guide/guide.html>._

# Table of Contents

* [Table of Contents](#table-of-contents)
* [What is Io?](#what-is-io)
	* [Example Code](#example-code)
	* [Quick Links](#quick-links)
* [Installing](#installing)
	* [From Source](#from-source)
		* [Linux Build Instructions](#linux-build-instructions)
* [Running Tests](#running-tests)

What is Io?
=====

Io is a dynamic prototype-based programming language in the same realm as
Smalltalk and Self. It revolves around the idea of message passing from object
to object.

For further information, the programming guide and reference manual can be found
in the docs folder.


Example Code
---
Basic Math

```Io
Io> 1 + 1
==> 2

Io> 2 sqrt
==> 1.4142135623730951
```

Lists

```Io
Io> d := List clone append(30, 10, 5, 20)
==> list(30, 10, 5, 20)

Io> d := d sort
==> list(5, 10, 20, 30)

Io> d select (>10)
==> list(20, 30)
```

Objects

```Io
Io> Contact := Object clone
==>  Contact_0x7fbc3bc8a6d0:
  type = "Contact"

Io> Contact name ::= nil
==> nil

Io> Contact address ::= nil
==> nil

Io> Contact city ::= nil
==> nil

Io> holmes := Contact clone setName("Holmes") setAddress("221B Baker St") setCity("London")
==>  Contact_0x7fbc3be2b470:
  address          = "221B Baker St"
  city             = "London"
  name             = "Holmes"

Io> Contact fullAddress := method(list(name, address, city) join("\n"))
==> method(
    list(name, address, city) join("\n")
)

Io> holmes fullAddress
==> Holmes
221B Baker St
London
```




Quick Links
---
* The Wikipedia page for Io has a good overview and shows a few interesting
  examples of the language:
  <https://en.wikipedia.org/wiki/Io_(programming_language)>.
* The entry on the c2 wiki has good discussion about the merits of the language:
  <http://wiki.c2.com/?IoLanguage>.


Installing
==========

From Source
---

First, make sure that this repo and all of its submodules have been cloned to
your computer by running `git clone`:

```
git clone https://github.com/guillermomolina/graal-io
```

### Linux Build Instructions

To prepare the project for building, run the following commands:

```
cd graal-io/     # To get into the cloned folder
make             # Build the project
```


Running Tests
===

NYI