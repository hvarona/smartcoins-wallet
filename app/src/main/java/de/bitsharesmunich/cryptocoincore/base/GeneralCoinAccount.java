package de.bitsharesmunich.cryptocoincore.base;

import com.google.gson.JsonObject;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import de.bitshares_munich.database.SCWallDatabase;
import de.bitshares_munich.database.SCWallDatabaseContract;

/**
 * Created by henry on 05/02/2017.
 */

public abstract class GeneralCoinAccount extends CryptoCoinAccount {
    protected int accountNumber;
    protected int lastExternalIndex;
    protected int lastChangeIndex;
    protected DeterministicKey accountKey;
    protected DeterministicKey externalKey;
    protected DeterministicKey changeKey;
    protected HashMap<Integer, GeneralCoinAddress> externalKeys = new HashMap();
    protected HashMap<Integer, GeneralCoinAddress> changeKeys = new HashMap();
    protected List<ChangeBalanceListener> changeBalanceListeners = new ArrayList();

    public GeneralCoinAccount(String id, String name, Coin coin, final AccountSeed seed, int accountNumber, int lastExternalIndex, int lastChangeIndex) {
        super(id, name, coin, seed);
        this.accountNumber = accountNumber;
        this.lastExternalIndex = lastExternalIndex;
        this.lastChangeIndex = lastChangeIndex;
        calculateAddresses();
    }

    private void calculateAddresses() {
        //BIP44
        DeterministicKey masterKey = HDKeyDerivation.createMasterPrivateKey(seed.getSeed());
        DeterministicKey purposeKey = HDKeyDerivation.deriveChildKey(masterKey, new ChildNumber(44, true));
        DeterministicKey coinKey = HDKeyDerivation.deriveChildKey(purposeKey, new ChildNumber(0, true));
        accountKey = HDKeyDerivation.deriveChildKey(coinKey, new ChildNumber(accountNumber, true));
        externalKey = HDKeyDerivation.deriveChildKey(accountKey, new ChildNumber(0, false));
        changeKey = HDKeyDerivation.deriveChildKey(accountKey, new ChildNumber(1, false));
    }

    public void calculateGapExternal(){
        if(externalKey == null){
            calculateAddresses();
        }
        for(int i = 0; i < lastExternalIndex;i++){
            if(!externalKeys.containsKey(i)){
                externalKeys.put(i,new GeneralCoinAddress(this,false,i,HDKeyDerivation.deriveChildKey(externalKey, new ChildNumber(i, false))));
            }
        }
    }

    public void calculateGapChange(){
        if(changeKey == null){
            calculateAddresses();
        }
        for(int i = 0; i < lastChangeIndex;i++){
            if(!changeKeys.containsKey(i)){
                changeKeys.put(i,new GeneralCoinAddress(this,false,i,HDKeyDerivation.deriveChildKey(changeKey, new ChildNumber(i, false))));
            }
        }
    }

    public List<GeneralCoinAddress> getAddresses() {
        calculateGapExternal();
        calculateGapChange();

        List<GeneralCoinAddress> addresses = new ArrayList();
        addresses.addAll(changeKeys.values());
        addresses.addAll(externalKeys.values());
        return addresses;
    }

    public void saveAddresses(SCWallDatabase db){
        for(GeneralCoinAddress externalAddress : externalKeys.values()){
            if(externalAddress.getId() == null || externalAddress.getId().isEmpty() || externalAddress.getId().equalsIgnoreCase("null")){
                db.putGeneralCoinAddress(externalAddress);
            }else{
                db.updateGeneralCoinAddress(externalAddress);
            }
        }

        for(GeneralCoinAddress changeAddress : changeKeys.values()){
            if(changeAddress.getId() == null || changeAddress.getId().isEmpty() || changeAddress.getId().equalsIgnoreCase("null")){
                db.putGeneralCoinAddress(changeAddress);
            }else{
                db.updateGeneralCoinAddress(changeAddress);
            }
        }
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    public int getLastExternalIndex() {
        return lastExternalIndex;
    }

    public int getLastChangeIndex() {
        return lastChangeIndex;
    }

    public JsonObject toJson() {
        JsonObject answer = new JsonObject();
        answer.addProperty("type", this.coin.name());
        answer.addProperty("name", this.name);
        answer.addProperty("accountNumber", this.accountNumber);
        answer.addProperty("changeIndex", this.lastChangeIndex);
        answer.addProperty("externalIndex", this.lastExternalIndex);
        return answer;
    }

    public List<GeneralTransaction> getTransactions(){
        List<GeneralTransaction> transactions = new ArrayList();
        for(GeneralCoinAddress address : externalKeys.values()){
            for(GIOTx giotx : address.getInputTransaction()){
                if(!transactions.contains(giotx.getTransaction())){
                    transactions.add(giotx.getTransaction());
                }
            }
            for(GIOTx giotx : address.getOutputTransaction()){
                if(!transactions.contains(giotx.getTransaction())){
                    transactions.add(giotx.getTransaction());
                }
            }
        }

        for(GeneralCoinAddress address : changeKeys.values()){
            for(GIOTx giotx : address.getInputTransaction()){
                if(!transactions.contains(giotx.getTransaction())){
                    transactions.add(giotx.getTransaction());
                }
            }
            for(GIOTx giotx : address.getOutputTransaction()){
                if(!transactions.contains(giotx.getTransaction())){
                    transactions.add(giotx.getTransaction());
                }
            };
        }

        Collections.sort(transactions, new TransactionsCustomComparator());

        return transactions;
    }

    public abstract String getAddressString(int index, boolean change);

    public abstract GeneralCoinAddress getAddress(int index, boolean change);

    public abstract NetworkParameters getNetworkParam();

    public class TransactionsCustomComparator implements Comparator<GIOTx> {
        @Override
        public int compare(GIOTx o1, GIOTx o2) {
            return o1.getTransaction().getDate().compareTo(o2.getTransaction().getDate());
        }
    }

    public void addChangeBalanceListener(ChangeBalanceListener listener){
        this.changeBalanceListeners.add(listener);
    }

    protected void _fireOnChangeBalance(Balance balance){
        for (ChangeBalanceListener listener : this.changeBalanceListeners){
            listener.balanceChange(balance);
        }
    }
}
