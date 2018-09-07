<a href="http://www.sackfix.org/"><img src ="http://www.sackfix.org/assets/sf_logo.png" /></a>
# SackFix Log (Re)Player

## What is this project?
An extension to the Panda Red's SackFix project(s)

Built on the SackFix project, this is a log player that reads from (human readable) logs and plays messages into a FIX session.
Great for testing specific FIX or trading system functionality or for stress testing.
Logs are human readable can be constructed using scripts from other logs or even using Excel.

To get started download:
* [LogPlayer](https://github.com/mysteryDave/sackfixlogplayer)
You will first need to compile and publishLocal these updated versions of SackFix, as these versions are not published anywhere:
* [Session](https://github.com/mysteryDave/sackfixsessions): All of the statemachines and message handling for the Fix Session.  ie the business logic lives here.
* [Messages](https://github.com/mysteryDave/sackfixmessages): Code generated Fix Messages for all versions of fix.
* [Common](https://github.com/mysteryDave/sackfix): The code generator and common classes - including all the code generated Fields.
Updated examples and fix tests can also be got from github:
* [Examples](https://github.com/mysteryDave/sackfixexamples): Use this as a base to develop your own FIX applications.
* [Tester](https://github.com/mysteryDave/sackfixtests): A very simple test suite to stress out any Session level implementation.


## What is sackfix?
SackFix is a Scala Fix Engine - ie a session layer fix implememtion including all messages and fields as strongly typed classes. 

Please visit [sackfix.org](http://www.sackfix.org) for documentation on the original project.

Original project repositories  are below:
* [Examples](https://github.com/PendaRed/sackfixexamples): This is all you need!
* [Tester](https://github.com/PendaRed/sackfixtests): A very simple test suite to stress out any Session level implementation.
* [Session](https://github.com/PendaRed/sackfixsessions): All of the statemachines and message handling for the Fix Session.  ie the business logic lives here.
* [Messages](https://github.com/PendaRed/sackfixmessages): Code generated Fix Messages for all versions of fix.
* [Common](https://github.com/PendaRed/sackfix): The code generator and common classes - including all the code generated Fields.

## Versions

JDK 10, Scala 2.12, SBT 1.2.1, Akka 2.5.14.   Feel free to upgrade.

<a href="http://www.sackfix.org/"><img src ="http://www.sackfix.org/assets/sackfix.png" /></a>
