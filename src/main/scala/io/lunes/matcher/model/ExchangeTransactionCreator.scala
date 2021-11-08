package io.lunes.matcher.model

import io.lunes.matcher.MatcherSettings
import io.lunes.settings.FunctionalitySettings
import io.lunes.state.Blockchain
import io.lunes.utx.UtxPool
import io.lunes.transaction.ValidationError
import io.lunes.transaction.assets.exchange.{ExchangeTransaction, Order}
import scorex.utils.{NTP, ScorexLogging}
import scorex.wallet.Wallet

trait ExchangeTransactionCreator extends ScorexLogging {
  val functionalitySettings: FunctionalitySettings
  val blockchain: Blockchain
  val wallet: Wallet
  val settings: MatcherSettings
  val utx: UtxPool
  private var txTime: Long = 0

  private def getTimestamp: Long = {
    txTime = Math.max(NTP.correctedTime(), txTime + 1)
    txTime
  }

  def createTransaction(
      submitted: LimitOrder,
      counter: LimitOrder): Either[ValidationError, ExchangeTransaction] = {
    wallet
      .privateKeyAccount(submitted.order.matcherPublicKey)
      .flatMap(matcherPrivateKey => {
        val price = counter.price
        val amount = math.min(submitted.amount, counter.amount)
        val (buy, sell) = Order.splitByType(submitted.order, counter.order)
        val (buyFee, sellFee) = calculateMatcherFee(buy, sell, amount: Long)
        ExchangeTransaction.create(matcherPrivateKey,
                                   buy,
                                   sell,
                                   price,
                                   amount,
                                   buyFee,
                                   sellFee,
                                   settings.orderMatchTxFee,
                                   getTimestamp)
      })
  }

  def calculateMatcherFee(buy: Order,
                          sell: Order,
                          amount: Long): (Long, Long) = {
    def calcFee(o: Order, amount: Long): Long = {
      val p = BigInt(amount) * o.matcherFee / o.amount
      p.toLong
    }

    (calcFee(buy, amount), calcFee(sell, amount))
  }
}