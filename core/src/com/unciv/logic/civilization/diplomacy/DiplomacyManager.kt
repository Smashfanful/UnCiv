package com.unciv.logic.civilization.diplomacy

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.trade.Trade
import com.unciv.logic.trade.TradeType
import com.unciv.models.Counter
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.TileResource
import com.unciv.models.gamebasics.tr

class DiplomacyManager() {
    @Transient lateinit var civInfo: CivilizationInfo
    lateinit var otherCivName:String
    var trades = ArrayList<Trade>()
    var diplomaticStatus = DiplomaticStatus.War

    fun clone(): DiplomacyManager {
        val toReturn = DiplomacyManager()
        toReturn.otherCivName=otherCivName
        toReturn.diplomaticStatus=diplomaticStatus
        toReturn.trades.addAll(trades.map { it.clone() })
        return toReturn
    }

    constructor(civilizationInfo: CivilizationInfo, OtherCivName:String) : this() {
        civInfo=civilizationInfo
        otherCivName=OtherCivName
    }

    //region pure functions
    fun turnsToPeaceTreaty(): Int {
        for(trade in trades)
            for(offer in trade.ourOffers)
                if(offer.name=="Peace Treaty" && offer.duration > 0) return offer.duration
        return 0
    }

    fun canDeclareWar() = (turnsToPeaceTreaty()==0 && diplomaticStatus != DiplomaticStatus.War)

    fun otherCiv() = civInfo.gameInfo.getCivilization(otherCivName)

    fun goldPerTurn():Int{
        var goldPerTurnForUs = 0
        for(trade in trades) {
            for (offer in trade.ourOffers.filter { it.type == TradeType.Gold_Per_Turn })
                goldPerTurnForUs -= offer.amount
            for (offer in trade.theirOffers.filter { it.type == TradeType.Gold_Per_Turn })
                goldPerTurnForUs += offer.amount
        }
        return goldPerTurnForUs
    }

    fun resourcesFromTrade(): Counter<TileResource> {
        val counter = Counter<TileResource>()
        for(trade in trades){
            for(offer in trade.ourOffers)
                if(offer.type== TradeType.Strategic_Resource || offer.type== TradeType.Luxury_Resource)
                    counter.add(GameBasics.TileResources[offer.name]!!,-offer.amount)
            for(offer in trade.theirOffers)
                if(offer.type== TradeType.Strategic_Resource || offer.type== TradeType.Luxury_Resource)
                    counter.add(GameBasics.TileResources[offer.name]!!,offer.amount)
        }
        return counter
    }
    //endregion

    //region state-changing functions
    fun removeUntenebleTrades(){
        val negativeCivResources = civInfo.getCivResources().filter { it.value<0 }.map { it.key.name }
        for(trade in trades.toList()) {
            for (offer in trade.ourOffers) {
                if (offer.type in listOf(TradeType.Luxury_Resource, TradeType.Strategic_Resource)
                    && offer.name in negativeCivResources){
                    trades.remove(trade)
                    val otherCivTrades = otherCiv().diplomacy[civInfo.civName]!!.trades
                    otherCivTrades.removeAll{ it.equals(trade.reverse()) }
                    civInfo.addNotification("One of our trades with [$otherCivName] has been cut short!".tr(),null, Color.GOLD)
                    otherCiv().addNotification("One of our trades with [${civInfo.civName}] has been cut short!".tr(),null, Color.GOLD)
                }
            }
        }
    }

    fun nextTurn(){
        for(trade in trades.toList()){
            for(offer in trade.ourOffers.union(trade.theirOffers).filter { it.duration>0 })
                offer.duration--

            if(trade.ourOffers.all { it.duration<=0 } && trade.theirOffers.all { it.duration<=0 }) {
                trades.remove(trade)
                civInfo.addNotification("One of our trades with [$otherCivName] has ended!".tr(),null, Color.YELLOW)
            }
        }
        removeUntenebleTrades()
    }

    fun declareWar(){
        diplomaticStatus = DiplomaticStatus.War
        val otherCiv = otherCiv()

        otherCiv.diplomacy[civInfo.civName]!!.diplomaticStatus = DiplomaticStatus.War
        otherCiv.addNotification("[${civInfo.civName}] has declared war on us!",null, Color.RED)
        otherCiv.popupAlerts.add(PopupAlert(AlertType.WarDeclaration,civInfo.civName))
    }
    //endregion
}