# Blockchain ledger

A simple in-memory blockchain ledger implemented using the ZIO library for pure functional programming in Scala. The 
purpose of this project is to show an example of how to work with ZIO, specifically it's STM 
(software transactional memory) and capabilities.

## Description

The blockchain ledger is a data structure to store accounts, transactions, and blocks. Once a determined number of 
transactions (**n**) has been reached the block is mined (closed/consolidated) and a new empty and *pending* block is 
generated and attached. This closed block stores references to these last transactions and a snapshot with all accounts 
(and their balances at block creation time). A hashcode is generated out of all this information (using also the 
previous hashcode). The integrity of these hashcodes guarantee the security of the entire ledger.   

### About ZIO

Zio is a library (or an entire framework I would say) for pure functional programming with amazing support for
async/concurrency. It also has STM (Software transactional memory) support; this is perhaps the most powerful feature 
of this framework. Here is a small example (from *ledger/core/Ledger*):

```scala
  /**
   * Steps:
   * - validate that the nick name is unique.
   * - update and get the counter to be used as the new account ID.
   * - store the account and commit.
   * 
   * @param nickName unique user nick name
   */
  def createAccount(nickName: String): ZIO[Clock, Exception, String] =
    STM.atomically {
      for {
        _        <- accounts.zmap.get(nickName)
                      .filterOrFail(_.isEmpty)(InvalidAccount("Nickname exists"))

        accNum   <- accounts.counter.updateAndGet(_ + 1)
        address  <- STM.succeed(Ledger.generateUUID(seed + accNum))

        acc  = Account(nickName, address, 0)
        _   <- accounts.zmap.put(acc.nickName, acc)
      } yield address
    } retry Schedule.duration(3.seconds)
```  
The steps to create an account can be found in the doc string. There are 2 participating references 
in this transaction: a TRef (accounts.counter) and a TMap (accounts.zmap). The TRef has a counter to 
keep track of the accounts created; the TMap is basically a HashMap that stores accounts and can 
participate in transactions. Transactions on the ledger are performed asynchronously by multiple threads (fibers). 
Notice how the entire sequence of steps is wrapped with `STM.atomically`; this call will commit the transaction 
or retry if it finds that any of the references involved (TRef and TMap) were modified by another thread. 
Also, notice `Schedule.duration(3.seconds)`; it basically states that the transaction will be retried only for 
3 seconds or return with the fail exception.  

Notice that with a traditional approach you would need to add locks to avoid race conditions. This small example shows 
how easy it is to work with transactions and fibers (concurrency and asynchronicity) in a pure functional style (
without all the esoteric terminology commonly related to FP!). 
    

### Usage & Examples

This ledger has a small DSL to create transactions and accounts (for reference go to **ledger/command/Command**). An 
example file with commands can be found in the resources' directory (**commands.txt**):

```bash
create-account antony
create-account carl
create-account mey
create-account judy

transaction master antony "add funds" 1000.00
transaction master carl "add funds" 1000.00
transaction master mey "add funds" 1000.00
transaction master judy "add funds" 1000.00

transaction antony mey "payment for services" 100.00
transaction antony judy "payment for services" 100.00
transaction antony carl "payment for services" 100.00
transaction carl mey "payment for services" 100.00

get-balance antony
get-balance carl
```

The final state of the ledger is displayed after the program ends. Each command outputs a message. This is the 
output of the file above:

```bash
CreateAccount(carl) ===>
	Success >> Address: d90865f0-dfdf-33f9-8bd5-8c2ae82c2bac
CreateAccount(judy) ===>
	Success >> Address: 76b9e12c-12da-377c-b803-436677e4e519
CreateAccount(mey) ===>
	Success >> Address: b7893e62-d720-34c3-b65b-00eed55a969c
CreateAccount(antony) ===>
	Success >> Address: c1fc91bd-b1e0-3d85-8f8c-d8cbea9cb067
ProcessTxn(master,antony,"add funds",1000.0) ===>
	Success >> Id: 1001
ProcessTxn(master,carl,"add funds",1000.0) ===>
	Success >> Id: 1003
ProcessTxn(master,mey,"add funds",1000.0) ===>
	Success >> Id: 1002
ProcessTxn(antony,mey,"payment for services",100.0) ===>
	Success >> Id: 1004
ProcessTxn(master,judy,"add funds",1000.0) ===>
	Success >> Id: 1006
ProcessTxn(antony,judy,"payment for services",100.0) ===>
	Success >> Id: 1005
ProcessTxn(carl,mey,"payment for services",100.0) ===>
	Success >> Id: 1007
ProcessTxn(antony,carl,"payment for services",100.0) ===>
	Success >> Id: 1008
GetBalance(carl) ===>
	Success >> Balance: 999.0
GetBalance(antony) ===>
	Success >> Balance: 697.0


====== BLOCKS ======

Block 3:	Block(
		, 
		Transactions(), 
		Balances())
Block 2:	Block(
		9212226b58f30aba92236f687625774067c8fd14351db1ef7b308c78e2f28a44, 
		Transactions(1005, 1006, 1007, 1008), 
		Balances((master,3.4028235E38), (antony,697.0), (carl,999.0), (judy,1100.0), (mey,1200.0)))
Block 1:	Block(
		0c45b42d0b5f32c1afb63cbceb970f39b11da5ba85d02e1f19c74917f50f1542, 
		Transactions(1001, 1002, 1003, 1004), 
		Balances((master,3.4028235E38), (antony,899.0), (carl,1000.0), (judy,0.0), (mey,1100.0)))
```


  

