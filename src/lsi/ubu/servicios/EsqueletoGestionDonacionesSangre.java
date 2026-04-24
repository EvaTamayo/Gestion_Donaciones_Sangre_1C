package lsi.ubu.servicios;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lsi.ubu.util.ExecuteScript;
import lsi.ubu.util.PoolDeConexiones;

import java.sql.PreparedStatement;
import java.sql.ResultSet;


/**
 * GestionDonacionesSangre:
 * Implementa la gestion de donaciones de sangre según el enunciado del ejercicio
 * 
 * @author <a href="mailto:jmaudes@ubu.es">Jesus Maudes</a>
 * @author <a href="mailto:rmartico@ubu.es">Raul Marticorena</a>
 * @author <a href="mailto:pgdiaz@ubu.es">Pablo Garcia</a>
 * @author <a href="mailto:srarribas@ubu.es">Sandra Rodriguez</a>
 * @version 1.5
 * @since 1.0 
 */
public class EsqueletoGestionDonacionesSangre {
	
	private static Logger logger = LoggerFactory.getLogger(EsqueletoGestionDonacionesSangre.class);

	private static final String script_path = "sql/";

	public static void main(String[] args) throws SQLException{		
		tests();

		System.out.println("FIN.............");
	}
	
	public static void realizar_donacion(String m_NIF, int m_ID_Hospital,
	        float m_Cantidad, Date m_Fecha_Donacion) throws SQLException {

	    PoolDeConexiones pool = PoolDeConexiones.getInstance();
	    Connection con = null;

	    PreparedStatement psDonante = null;
	    PreparedStatement psHospital = null;
	    PreparedStatement psUltimaDonacion = null;
	    PreparedStatement psInsertDonacion = null;
	    PreparedStatement psUpdateReserva = null;
	    ResultSet rs = null;

	    try {
	        con = pool.getConnection();

	        //  Validar cantidad: debe estar entre 0 y 0,45
	        if (m_Cantidad < 0 || m_Cantidad > 0.45f) {
	            throw new GestionDonacionesSangreException(
	                    GestionDonacionesSangreException.VALOR_CANTIDAD_DONACION_INCORRECTO);
	        }

	        //  Comprobar que existe el donante y obtener su tipo de sangre
	        int idTipoSangre;

	        psDonante = con.prepareStatement(
	                "SELECT id_tipo_sangre FROM donante WHERE nif = ?");
	        psDonante.setString(1, m_NIF);
	        rs = psDonante.executeQuery();

	        if (!rs.next()) {
	            throw new GestionDonacionesSangreException(
	                    GestionDonacionesSangreException.DONANTE_NO_EXISTE);
	        }

	        idTipoSangre = rs.getInt("id_tipo_sangre");
	        rs.close();
	        rs = null;

	        //  Comprobar que existe el hospital
	        psHospital = con.prepareStatement(
	                "SELECT id_hospital FROM hospital WHERE id_hospital = ?");
	        psHospital.setInt(1, m_ID_Hospital);
	        rs = psHospital.executeQuery();

	        if (!rs.next()) {
	            throw new GestionDonacionesSangreException(
	                    GestionDonacionesSangreException.HOSPITAL_NO_EXISTE);
	        }

	        rs.close();
	        rs = null;

	        // Comprobar que el donante no haya donado en los últimos 15 días
	        java.sql.Date fechaDonacionSql = new java.sql.Date(m_Fecha_Donacion.getTime());
	        long quinceDiasMs = 15L * 24 * 60 * 60 * 1000;
	        java.sql.Date fechaLimite = new java.sql.Date(m_Fecha_Donacion.getTime() - quinceDiasMs);

	        psUltimaDonacion = con.prepareStatement(
	                "SELECT COUNT(*) AS total " +
	                "FROM donacion " +
	                "WHERE nif_donante = ? AND fecha_donacion BETWEEN ? AND ?");
	        psUltimaDonacion.setString(1, m_NIF);
	        psUltimaDonacion.setDate(2, fechaLimite);
	        psUltimaDonacion.setDate(3, fechaDonacionSql);
	        rs = psUltimaDonacion.executeQuery();
	        rs.next();

	        if (rs.getInt("total") > 0) {
	            throw new GestionDonacionesSangreException(
	                    GestionDonacionesSangreException.DONANTE_EXCEDE);
	        }

	        rs.close();
	        rs = null;

	        // Insertar la nueva donación
	        psInsertDonacion = con.prepareStatement(
	                "INSERT INTO donacion (id_donacion, nif_donante, cantidad, fecha_donacion) " +
	                "VALUES (seq_donacion.nextval, ?, ?, ?)");
	        psInsertDonacion.setString(1, m_NIF);
	        psInsertDonacion.setFloat(2, m_Cantidad);
	        psInsertDonacion.setDate(3, fechaDonacionSql);
	        psInsertDonacion.executeUpdate();

	        // Incrementar la reserva del hospital para el tipo de sangre del donante
	        psUpdateReserva = con.prepareStatement(
	                "UPDATE reserva_hospital " +
	                "SET cantidad = cantidad + ? " +
	                "WHERE id_hospital = ? AND id_tipo_sangre = ?");
	        psUpdateReserva.setFloat(1, m_Cantidad);
	        psUpdateReserva.setInt(2, m_ID_Hospital);
	        psUpdateReserva.setInt(3, idTipoSangre);

	        int filas = psUpdateReserva.executeUpdate();
	        if (filas == 0) {
	            throw new SQLException("No existe reserva_hospital para ese hospital y tipo de sangre");
	        }

	        // Si todo va bien, confirmar cambios
	        con.commit();

	    } catch (SQLException e) {
	        // En cualquier excepción, rollback
	        if (con != null) {
	            con.rollback();
	        }

	        // Las excepciones propias se relanzan sin log extra
	        if (e instanceof GestionDonacionesSangreException) {
	            throw e;
	        }

	        // Las SQLExceptions normales sí se registran
	        logger.error(e.getMessage());
	        throw e;

	    } finally {
	        if (rs != null) rs.close();
	        if (psDonante != null) psDonante.close();
	        if (psHospital != null) psHospital.close();
	        if (psUltimaDonacion != null) psUltimaDonacion.close();
	        if (psInsertDonacion != null) psInsertDonacion.close();
	        if (psUpdateReserva != null) psUpdateReserva.close();
	        if (con != null) con.close();
	    }
	}
	
	public static void anular_traspaso(int m_ID_Tipo_Sangre, int m_ID_Hospital_Origen,int m_ID_Hospital_Destino,
			Date m_Fecha_Traspaso)
			throws SQLException {
		
		PoolDeConexiones pool = PoolDeConexiones.getInstance();
		Connection con=null;

	
		try{
			con = pool.getConnection();
			//Completar por el alumno
			
		} catch (SQLException e) {
			//Completar por el alumno			
			
			logger.error(e.getMessage());
			throw e;		

		} finally {
			/*A rellenar por el alumno*/
		}		
	}
	
	public static void consulta_traspasos(String m_Tipo_Sangre)
			throws SQLException {

				
		PoolDeConexiones pool = PoolDeConexiones.getInstance();
		Connection con=null;

	
		try{
			con = pool.getConnection();
			//Completar por el alumno
			
		} catch (SQLException e) {
			//Completar por el alumno			
			
			logger.error(e.getMessage());
			throw e;		

		} finally {
			/*A rellenar por el alumno*/
		}		
	}
	
	static public void creaTablas() {
		ExecuteScript.run(script_path + "gestion_donaciones_sangre.sql");
	}

	static void tests() throws SQLException{
		creaTablas();
		
		PoolDeConexiones pool = PoolDeConexiones.getInstance();		
		
		//Relatar caso por caso utilizando el siguiente procedure para inicializar los datos
		
		CallableStatement cll_reinicia=null;
		Connection conn = null;
		
		try {
			//Reinicio filas
			conn = pool.getConnection();
			cll_reinicia = conn.prepareCall("{call inicializa_test}");
			cll_reinicia.execute();
		} catch (SQLException e) {				
			logger.error(e.getMessage());			
		} finally {
			if (cll_reinicia!=null) cll_reinicia.close();
			if (conn!=null) conn.close();
		
		}	
		//Comprovación simple de caso correcto (sustituir por tests mas adelante)
		realizar_donacion("12345678A", 1, 0.30f, java.sql.Date.valueOf("2025-02-20"));
		System.out.println("realizar_donacion ejecutado");
	}
}
