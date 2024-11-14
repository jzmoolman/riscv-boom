package epmp

import boom.common.{BoomBundle, BoomModule}
import chisel3._
import freechips.rocketchip.rocket.CSR
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy.{IdRange, LazyModule, LazyModuleImp}
import freechips.rocketchip.tilelink.{TLClientNode, TLMasterParameters, TLMasterPortParameters}

class EPMPIO(implicit p: Parameters) extends BoomBundle()(p){
  val rw = new Bundle {
    val addr = Input(UInt(CSR.ADDRSZ.W))
    val cmd = Input(Bits(CSR.SZ.W))
    val rdata = Output(Bits(xLen.W))
    val wdata = Input(Bits(xLen.W))
  }
}

class EPMP(staticIdForMetadataUseOnly: Int)(implicit p: Parameters) extends LazyModule {
  protected def epmpClientParameters = Seq(TLMasterParameters.v1(
    name          = s"Core ${staticIdForMetadataUseOnly} ePMP",
    sourceId      = IdRange(0,1),
    requestFifo   = true))
  val node = TLClientNode(Seq(TLMasterPortParameters.v1(
    epmpClientParameters,
    minLatency = 1)))

  lazy val module = new EPMPModule(this)
}

class EPMPModule(outer: EPMP) extends LazyModuleImp(outer) {
  val io = IO(new EPMPIO)
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
  }
}

