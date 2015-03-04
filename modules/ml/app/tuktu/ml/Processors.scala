package tuktu.ml

import play.api.libs.json.JsValue
import play.api.libs.iteratee.Enumeratee
import tuktu.api._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class LogisticRegressionTrainProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def initialize(config: JsValue) = {
        
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
        // Train on the current batch
        
        Future {data}
    })
}

class LogisticRegressionApplyProcessor(resultName: String) extends BaseProcessor(resultName) {
    override def initialize(config: JsValue) = {
        
    }
    
    override def processor(): Enumeratee[DataPacket, DataPacket] = Enumeratee.mapM(data => {
        // Classify all items in this batch
        
        Future {data}
    })
}