package mmc

import boom.common.{BoomBundle, BoomModule}
import chisel3._
import chisel3.{Bits, Bundle, Input, Output, UInt}
import freechips.rocketchip.diplomacy.{IdRange, LazyModule, LazyModuleImp}
import freechips.rocketchip.rocket.CSR
import freechips.rocketchip.tilelink.{TLClientNode, TLEdgeOut, TLMasterParameters, TLMasterPortParameters}
import org.chipsalliance.cde.config.Parameters


class MMCIO(implicit p: Parameters) extends BoomBundle()(p){
  val rw = new Bundle {
    val addr = Input(UInt(CSR.ADDRSZ.W))
    val cmd = Input(Bits(CSR.SZ.W))
    val rdata = Output(Bits(xLen.W))
    val wdata = Input(Bits(xLen.W))
  }
}

class MMC(staticIdForMetadataUseOnly: Int)(implicit p: Parameters) extends LazyModule {
  protected def mmcClientParameters = Seq(TLMasterParameters.v1(
    name          = s"Core ${staticIdForMetadataUseOnly} MMC",
    sourceId      = IdRange(0,1),
    requestFifo   = true))
  val node = TLClientNode(Seq(TLMasterPortParameters.v1(
    mmcClientParameters,
    minLatency = 1)))

  lazy val module = new MMCModule(this)
}

class MMCModule(outer: MMC) extends LazyModuleImp(outer) {
  val io = IO(new MMCIO)
  implicit val edge = outer.node.edges.out(0)
  val (tl_out, _) = outer.node.out(0)
  io.rw.rdata := DontCare
  val csr_addr = RegNext(io.rw.addr)
  dontTouch(csr_addr)
  val csr_cmd = RegNext(io.rw.cmd)
  dontTouch(csr_cmd)
  val csr_wdata = RegNext(io.rw.wdata)
  dontTouch(csr_wdata)

  tl_out.a.valid := false.B
  tl_out.a.bits := DontCare
  when(io.rw.cmd  === 5.U) {
     tl_out.a.valid := true.B;
     tl_out.a.bits := edge.AcquireBlock(
      fromSource      =  1.U,
      toAddress       = io.rw.addr,
      lgSize          = 8.U,
      growPermissions = 0.U)._2
//    growPermissions = grow_param)._2

  }

}

