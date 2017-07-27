package de.bitsharesmunich.cryptocoincore.litecoin;

import android.content.Context;

import de.bitsharesmunich.cryptocoincore.base.AccountSeed;
import de.bitsharesmunich.cryptocoincore.base.Balance;
import static de.bitsharesmunich.cryptocoincore.base.Coin.LITECOIN;
import de.bitsharesmunich.cryptocoincore.base.GTxIO;
import de.bitsharesmunich.cryptocoincore.base.GeneralCoinAccount;
import de.bitsharesmunich.cryptocoincore.base.GeneralCoinAddress;
import de.bitsharesmunich.cryptocoincore.insightapi.BroadcastTransaction;
import de.bitsharesmunich.cryptocoincore.insightapi.GetEstimateFee;
import de.bitsharesmunich.graphenej.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.CustomNetworkParameters;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.script.Script;

/**
 *
 */
public class LiteCoinAccount extends GeneralCoinAccount{
    
    private NetworkParameters param = CustomNetworkParameters.fromCoin(LITECOIN);
    
    private static final int LITE_COIN_NUMBER = 2;

    LiteCoinAccount(long id, String name, AccountSeed seed, int accountNumber, int lastExternalIndex, int lastChangeIndex) {
        super(id, name, LITECOIN, seed, LITE_COIN_NUMBER, accountNumber, lastExternalIndex, lastChangeIndex);

    }

    public LiteCoinAccount(final AccountSeed seed, String name) {
        this(seed, name, false);
    }

    LiteCoinAccount(final AccountSeed seed, String name, boolean importing) {
        super(-1, name, LITECOIN, seed, LITE_COIN_NUMBER, 0, 0, 0);
        if (importing) {
            //TODO calculate the number of account
        }
    }

    @Override
    public List<Balance> getBalance() {
        long unconfirmedAmount = 0;
        long confirmedAmount = 0;
        int lessConfirmed = -1;
        Date lastDate = null;
        for (GeneralCoinAddress key : mExternalKeys.values()) {
            unconfirmedAmount += key.getUnconfirmedBalance();
            confirmedAmount += key.getConfirmedBalance();
            int keyLessConf = key.getLessConfirmed();
            if(keyLessConf != -1 && (lessConfirmed== -1 || keyLessConf< lessConfirmed)){
                lessConfirmed = keyLessConf;
            }
            if(lastDate == null || (key.getLastDate()!= null && lastDate.before(key.getLastDate()))){
                lastDate = key.getLastDate();
            }
        }

        for (GeneralCoinAddress key : mChangeKeys.values()) {
            unconfirmedAmount += key.getUnconfirmedBalance();
            confirmedAmount += key.getConfirmedBalance();
            int keyLessConf = key.getLessConfirmed();
            if(keyLessConf != -1 && (lessConfirmed== -1 || keyLessConf< lessConfirmed)){
                lessConfirmed = keyLessConf;
            }
            if(lastDate == null || (key.getLastDate()!= null && lastDate.before(key.getLastDate()))){
                lastDate = key.getLastDate();
            }
        }

        Balance balance = new Balance();
        balance.setType(LITECOIN);
        balance.setDate(lastDate);
        balance.setConfirmedAmount(confirmedAmount);
        balance.setUnconfirmedAmount(unconfirmedAmount);
        balance.setLessConfirmed(lessConfirmed);
        List<Balance> balances = new ArrayList();
        balances.add(balance);
        return balances;
    }

    @Override
    public String getNextRecieveAddress() {
        if (!mExternalKeys.containsKey(mLastExternalIndex)) {
            mExternalKeys.put(mLastExternalIndex, new GeneralCoinAddress(this, false, mLastExternalIndex, HDKeyDerivation.deriveChildKey(mExternalKey, new ChildNumber(mLastExternalIndex, false))));
        }

        //Finding the next unused address
        while(mExternalKeys.get(mLastExternalIndex).getTransactionInput().size()>0){
            ++mLastExternalIndex;
            if (!mExternalKeys.containsKey(mLastExternalIndex)) {
                mExternalKeys.put(mLastExternalIndex, new GeneralCoinAddress(this, false, mLastExternalIndex, HDKeyDerivation.deriveChildKey(mExternalKey, new ChildNumber(mLastExternalIndex, false))));
            }
        }
        return mExternalKeys.get(mLastExternalIndex).getAddressString(param);
    }

    @Override
    public String getNextChangeAddress() {
        if (!mChangeKeys.containsKey(mLastChangeIndex)) {
            mChangeKeys.put(mLastChangeIndex, new GeneralCoinAddress(this, true, mLastChangeIndex, HDKeyDerivation.deriveChildKey(mChangeKey, new ChildNumber(mLastChangeIndex, false))));
        }

        //Finding the next unused address
        while(mChangeKeys.get(mLastChangeIndex).getTransactionInput().size()>0){
            ++mLastChangeIndex;
            if (!mChangeKeys.containsKey(mLastChangeIndex)) {
                mChangeKeys.put(mLastChangeIndex, new GeneralCoinAddress(this, true, mLastChangeIndex, HDKeyDerivation.deriveChildKey(mChangeKey, new ChildNumber(mLastChangeIndex, false))));
            }
        }
        return mChangeKeys.get(mLastChangeIndex).getAddressString(param);
    }

    @Override
    public void send(String toAddress, de.bitsharesmunich.cryptocoincore.base.Coin coin, long amount, String memo, Context context) {
        if(coin.equals(LITECOIN)){
            Transaction tx = new Transaction(param);

            long currentAmount = 0;
            long fee = -1;
            try {
                fee = 226 * GetEstimateFee.getEstimateFee(LITECOIN)/1000;
            } catch (IOException ex) {
                //TODO error getting fee
            }
            if(fee == -1){
                fee = (long)(0.0001 * Math.pow(10, LITECOIN.getPrecision()));
            }

            List<GeneralCoinAddress> addresses = getAddresses();
            List<GTxIO> utxos = new ArrayList();
            for(GeneralCoinAddress address : addresses){
                List<GTxIO> addrUtxos = address.getUTXos();
                for(GTxIO addrUtxo : addrUtxos){
                    utxos.add(addrUtxo);
                    currentAmount += addrUtxo.getAmount();
                    if(currentAmount >= amount+ fee){
                        break;
                    }
                }
                if(currentAmount >= amount + fee){
                    break;
                }
            }


            if(currentAmount< amount + fee){
                //TODO error amount bigger than avaible
                return;
            }

            //String to an address
            Address toAddr = Address.fromBase58(param, toAddress);
            tx.addOutput(Coin.valueOf(amount), toAddr);

            if(memo != null && !memo.isEmpty()){
                if(memo.length()>40){
                    memo = memo.substring(0,40);
                }
                byte[]scriptByte = new byte[memo.length()+2];
                scriptByte[0] = 0x6a;
                scriptByte[1] = (byte) memo.length();
                System.arraycopy(memo.getBytes(),0,scriptByte,2,memo.length());
                Script memoScript = new Script(scriptByte);
                tx.addOutput(Coin.valueOf(0),memoScript);
            }

            //Change address
            long remain = currentAmount - amount - fee;
            if( remain > 0 ) {
                System.out.println("SENDTEST: remain : " + remain);
                Address changeAddr = Address.fromBase58(param, getNextChangeAddress());
                System.out.println("SENDTEST: NC " + changeAddr.toBase58());
                tx.addOutput(Coin.valueOf(remain), changeAddr);
            }

            for(GTxIO utxo: utxos) {
                Sha256Hash txHash = Sha256Hash.wrap(utxo.getTransaction().getTxid());
                Script script = new Script(Util.hexToBytes(utxo.getScriptHex()));
                TransactionOutPoint outPoint = new TransactionOutPoint(param, utxo.getIndex(), txHash);
                if(utxo.getAddress().getKey().isPubKeyOnly()){
                    if(utxo.getAddress().isIsChange()){
                        utxo.getAddress().setKey(HDKeyDerivation.deriveChildKey(mChangeKey, new ChildNumber(utxo.getAddress().getIndex(), false)));
                    }else{
                        utxo.getAddress().setKey(HDKeyDerivation.deriveChildKey(mExternalKey, new ChildNumber(utxo.getAddress().getIndex(), false)));
                    }
                }
                tx.addSignedInput(outPoint, script, utxo.getAddress().getKey(), Transaction.SigHash.ALL, true);
            }
            System.out.println("SENDTEST: " + Util.bytesToHex(tx.bitcoinSerialize()));

            BroadcastTransaction brTrans = new BroadcastTransaction(Util.bytesToHex(tx.bitcoinSerialize()),this,context);
            brTrans.start();

        }else{
            //TODO error bad coin argument
        }
    }

    public Address getAddress() {
        return mExternalKeys.get(mLastExternalIndex).getAddress(param);
    }

    @Override
    public String toString() {
        return "BitcoinAccount{"
                + "name=" + mName
                + ", idSeed=" + mSeed.getId()
                + ", AccountNumber=" + mAccountNumber
                + ", nextAddress=" + getNextRecieveAddress()
                + ", param=" + param + '}';
    }

    @Override
    public String getAddressString(int index, boolean change) {
        if (change) {
            if (!mChangeKeys.containsKey(index)) {
                mChangeKeys.put(index, new GeneralCoinAddress(this, true, index, HDKeyDerivation.deriveChildKey(mChangeKey, new ChildNumber(index, false))));
            }
            return mChangeKeys.get(index).getAddressString(param);
        } else {
            if (!mExternalKeys.containsKey(index)) {
                mExternalKeys.put(index, new GeneralCoinAddress(this, false, index, HDKeyDerivation.deriveChildKey(mExternalKey, new ChildNumber(index, false))));
            }
            return mExternalKeys.get(index).getAddressString(param);
        }
    }

    @Override
    public NetworkParameters getNetworkParam() {
        return param;
    }

    @Override
    public GeneralCoinAddress getAddress(int index, boolean change) {
        if (change) {
            if (!mChangeKeys.containsKey(index)) {
                mChangeKeys.put(index, new GeneralCoinAddress(this, true, index, HDKeyDerivation.deriveChildKey(mChangeKey, new ChildNumber(index, false))));
            }
            return mChangeKeys.get(index);
        } else {
            if (!mExternalKeys.containsKey(index)) {
                mExternalKeys.put(index, new GeneralCoinAddress(this, false, index, HDKeyDerivation.deriveChildKey(mExternalKey, new ChildNumber(index, false))));
            }
            return mExternalKeys.get(index);
        }
    }
}
