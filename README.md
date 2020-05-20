# Blockchain ledger

A basic in-memory blockchain ledger implemented using the ZIO library for pure functional programming in Scala. The 
purpose of this project is to show an example of how to work with ZIO, specifically it's STM 
(software transactional memory) capabilities.

## Description

The blockchain ledger is a data structure to store accounts, transactions, and blocks. Once a determined number of 
transactions (**n**) has been reached the block is mined (closed/consolidated) and a new empty and *pending* block is 
generated. This closed block stores references to these last transactions and a snapshot with all accounts (and their 
balances at block creation time). A hashcode is generated out of all this information plus the hashcode of the 
previous block. The integrity of these hashcodes guarantee the security of the entire ledger.   

### About ZIO

...    

### Examples

...
