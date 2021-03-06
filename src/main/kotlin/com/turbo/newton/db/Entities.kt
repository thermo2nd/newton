package com.turbo.newton.db

import com.turbo.binance.model.Candle
import com.turbo.newton.db.Evaluations.index
import com.turbo.toJodaDateTime
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.*

object DatabaseManager {

  fun insertHistoryGroup(_quoteAsset: String, _baseAsset: String, _description: String): HistoryGroup {
    val hg = HistoryGroup.new {
      quoteAsset = _quoteAsset
      baseAsset = _baseAsset
      description = _description
      registeredDatetime = DateTime.now()
    }
    return hg
  }

  fun selectHistoryGroup(): List<HistoryGroup> {
    return HistoryGroup.all().asSequence().toList()
  }

  fun insertCandleHistory(hg: HistoryGroup, candle: Candle): CandleHistory {
    val candleHistory = CandleHistory.new {
      historyGroup = hg
      openTime = candle.openTime.toJodaDateTime()
      closeTime = candle.closeTime.toJodaDateTime()
      highPrice = candle.highPrice
      lowPrice = candle.lowPrice
      openPrice = candle.openPrice
      closePrice = candle.closePrice
      volume = candle.volume
      quoteAssetVolume = candle.quoteAssetVolume
      numberOfTrades = candle.numberOfTrades
      takerBuyBaseAssetVolume = candle.takerBuyBaseAssetVolume
      takerBuyQuoteAssetVolume = candle.takerBuyQuoteAssetVolume
      ignore = candle.ignore
    }
    return candleHistory
  }

  fun selectCandleHistoryByHistoryGroup(historyGroupId: Int): List<Candle> {
    val listOfCandleHistory = CandleHistory.find { CandleHistories.historyGroup eq historyGroupId }.sortedBy { it.openTime }.asSequence().toList()
    return listOfCandleHistory.map { Candle.buildFromCandleHistory(it) }
  }

  fun selectCandleHistoryByHistoryGroupAndDuration(historyGroupId: Int, startInclusive: ZonedDateTime, endExclusive: ZonedDateTime): List<Candle> {
    val listOfCandleHistory = CandleHistory.find {
      (CandleHistories.historyGroup eq historyGroupId) and
          (CandleHistories.openTime greaterEq startInclusive.toJodaDateTime()) and
          (CandleHistories.openTime less endExclusive.toJodaDateTime())
    }.sortedBy { it.openTime }.asSequence().toList()
    return listOfCandleHistory.map { Candle.buildFromCandleHistory(it) }
  }

  fun insertEvaluation(_testId: String, _openTime: ZonedDateTime, _closeTime: ZonedDateTime, _myReturn: BigDecimal, _marketReturn: BigDecimal, _price: BigDecimal, _totalBalance: BigDecimal, _baseBalance: BigDecimal, _quoteBalance: BigDecimal, _basePosition: Int, _quotePosition: Int): Evaluation {
    return Evaluation.new {
      testId = _testId
      openTime = _openTime.toJodaDateTime()
      closeTime = _closeTime.toJodaDateTime()
      myReturn = _myReturn
      marketReturn = _marketReturn
      price = _price
      totalBalance = _totalBalance
      baseBalance = _baseBalance
      quoteBalance = _quoteBalance
      basePosition = _basePosition
      quotePosition = _quotePosition
    }
  }

  fun insertBacktest(_testId: String, _symbol: String, _openTime: ZonedDateTime, _closeTime: ZonedDateTime, _myReturn: BigDecimal, _marketReturn: BigDecimal): Backtest {
    return Backtest.new {
      testId = _testId
      symbol = _symbol
      openTime = _openTime.toJodaDateTime()
      closeTime = _closeTime.toJodaDateTime()
      myReturn = _myReturn
      marketReturn = _marketReturn
    }
  }

  fun connect(url: String, user: String, password: String, driver: String, withClean: Boolean) {
    Database.connect(
        url = url,
        user = user,
        password = password,
        driver = driver
    )
    transaction {
      addLogger(StdOutSqlLogger)
      if(withClean) {
        clean()
      }
    }
  }
  private fun clean() {
    val conn = TransactionManager.current().connection
    val statement = conn.createStatement()
    statement.execute("SET FOREIGN_KEY_CHECKS = 0")

    val listOfTable = listOf(
        HistoryGroups,
        CandleHistories,
        Backtests,
        Evaluations
    )

    listOfTable.forEach {table ->
      SchemaUtils.drop(table)
      SchemaUtils.create(table)
    }

    statement.execute("SET FOREIGN_KEY_CHECKS = 1")
  }
}

object HistoryGroups : IntIdTable() {
  val quoteAsset: Column<String> = varchar(name = "quoteAsset", collate = "utf8_general_ci", length = 20)
  val baseAsset: Column<String> = varchar(name = "baseAsset", collate = "utf8_general_ci", length = 20)
  val description: Column<String> = text(name = "description", collate = "utf8_general_ci")
  val registeredDatetime = datetime("registeredDatetime")
}

class HistoryGroup(id: EntityID<Int>): IntEntity(id) {
  companion object : IntEntityClass<HistoryGroup>(HistoryGroups)
  var quoteAsset by HistoryGroups.quoteAsset
  var baseAsset by HistoryGroups.baseAsset
  var description by HistoryGroups.description
  var registeredDatetime by HistoryGroups.registeredDatetime
}

object CandleHistories : IntIdTable() {
  val historyGroup = reference(name = "historyGroupId", foreign = HistoryGroups)
  val openTime: Column<DateTime> = datetime("openTime").index()
  val closeTime: Column<DateTime> = datetime("closeTime").index()
  val highPrice: Column<BigDecimal> = decimal(name = "highPrice", precision = 20, scale = 8)
  val lowPrice: Column<BigDecimal> = decimal(name = "lowPrice", precision = 20, scale = 8)
  val openPrice: Column<BigDecimal> = decimal(name = "openPrice", precision = 20, scale = 8)
  val closePrice: Column<BigDecimal> = decimal(name = "closePrice", precision = 20, scale = 8)
  val volume: Column<BigDecimal> = decimal(name = "volume", precision = 20, scale = 8)
  val quoteAssetVolume: Column<BigDecimal> = decimal(name = "quoteAssetVolume", precision = 20, scale = 8)
  val numberOfTrades: Column<Int> = integer(name = "numberOfTrades")
  val takerBuyBaseAssetVolume: Column<BigDecimal> = decimal(name = "takerBuyBaseAssetVolume", precision = 20, scale = 8)
  val takerBuyQuoteAssetVolume: Column<BigDecimal> = decimal(name = "takerBuyQuoteAssetVolume", precision = 20, scale = 8)
  val ignore: Column<BigDecimal> = decimal(name = "ignore", precision = 20, scale = 8)
}

class CandleHistory(id: EntityID<Int>): IntEntity(id) {
  companion object : IntEntityClass<CandleHistory>(CandleHistories)
  var historyGroup by HistoryGroup referencedOn CandleHistories.historyGroup
  var openTime by CandleHistories.openTime
  var closeTime by CandleHistories.closeTime
  var highPrice by CandleHistories.highPrice
  var lowPrice by CandleHistories.lowPrice
  var openPrice by CandleHistories.openPrice
  var closePrice by CandleHistories.closePrice
  var volume by CandleHistories.volume
  var quoteAssetVolume by CandleHistories.quoteAssetVolume
  var numberOfTrades by CandleHistories.numberOfTrades
  var takerBuyBaseAssetVolume by CandleHistories.takerBuyBaseAssetVolume
  var takerBuyQuoteAssetVolume by CandleHistories.takerBuyQuoteAssetVolume
  var ignore by CandleHistories.ignore
}

object Evaluations: IntIdTable() {
  val testId: Column<String> = text(name = "testId", collate = "utf8_general_ci")
  val openTime: Column<DateTime> = datetime("openTime").index()
  val closeTime: Column<DateTime> = datetime("closeTime").index()
  val myReturn: Column<BigDecimal> = decimal(name = "myReturn", precision = 20, scale = 8)
  val marketReturn: Column<BigDecimal> = decimal(name = "marketReturn", precision = 20, scale = 8)
  val price: Column<BigDecimal> = decimal(name = "price", precision = 20, scale = 8)
  val totalBalance: Column<BigDecimal> = decimal(name = "totalBalance", precision = 20, scale = 8)
  val baseBalance: Column<BigDecimal> = decimal(name = "baseBalance", precision = 20, scale = 8)
  val quoteBalance: Column<BigDecimal> = decimal(name = "quoteBalance", precision = 20, scale = 8)
  val basePosition: Column<Int> = integer(name = "basePosition")
  val quotePosition: Column<Int> = integer(name = "quotePosition")
}

class Evaluation(id: EntityID<Int>): IntEntity(id) {
  companion object : IntEntityClass<Evaluation>(Evaluations)
  var testId by Evaluations.testId
  var openTime by Evaluations.openTime
  var closeTime by Evaluations.closeTime
  var myReturn by Evaluations.myReturn
  var marketReturn by Evaluations.marketReturn
  var price by Evaluations.price
  var totalBalance by Evaluations.totalBalance
  var baseBalance by Evaluations.baseBalance
  var quoteBalance by Evaluations.quoteBalance
  var basePosition by Evaluations.basePosition
  var quotePosition by Evaluations.quotePosition
}

object Backtests: IntIdTable() {
  val testId: Column<String> = Backtests.text(name = "testId", collate = "utf8_general_ci")
  val symbol: Column<String> = Backtests.text(name = "symbol", collate = "utf8_general_ci")
  val openTime: Column<DateTime> = Backtests.datetime("openTime").index()
  val closeTime: Column<DateTime> = Backtests.datetime("closeTime").index()
  val myReturn: Column<BigDecimal> = Backtests.decimal(name = "myReturn", precision = 20, scale = 8)
  val marketReturn: Column<BigDecimal> = Backtests.decimal(name = "marketReturn", precision = 20, scale = 8)
}

class Backtest(id: EntityID<Int>): IntEntity(id) {
  companion object : IntEntityClass<Backtest>(Backtests)
  var testId by Backtests.testId
  var symbol by Backtests.symbol
  var openTime by Backtests.openTime
  var closeTime by Backtests.closeTime
  var myReturn by Backtests.myReturn
  var marketReturn by Backtests.marketReturn
}