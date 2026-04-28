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
		    PreparedStatement psTipoSangre = null;
		    PreparedStatement psHospitalOrigen = null;
		    PreparedStatement psHospitalDestino = null;
		    PreparedStatement psTraspasos = null;
		    PreparedStatement psUpdateDestino = null;
		    PreparedStatement psUpdateOrigen = null;
		    PreparedStatement psDeleteTraspasos = null;
		    ResultSet rs = null;
	
		try{
			con = pool.getConnection();

		        // Comprobar que existe el tipo de sangre
		        psTipoSangre = con.prepareStatement(
		                "SELECT id_tipo_sangre FROM tipo_sangre WHERE id_tipo_sangre = ?");
		        psTipoSangre.setInt(1, m_ID_Tipo_Sangre);
		        rs = psTipoSangre.executeQuery();

		        if (!rs.next()) {
		            throw new GestionDonacionesSangreException(
		                    GestionDonacionesSangreException.TIPO_SANGRE_NO_EXISTE);
		        }

		        rs.close();
		        rs = null;

		        // Comprobar que existe el hospital origen
		        psHospitalOrigen = con.prepareStatement(
		                "SELECT id_hospital FROM hospital WHERE id_hospital = ?");
		        psHospitalOrigen.setInt(1, m_ID_Hospital_Origen);
		        rs = psHospitalOrigen.executeQuery();

		        if (!rs.next()) {
		            throw new GestionDonacionesSangreException(
		                    GestionDonacionesSangreException.HOSPITAL_NO_EXISTE);
		        }

		        rs.close();
		        rs = null;

		        // Comprobar que existe el hospital destino
		        psHospitalDestino = con.prepareStatement(
		                "SELECT id_hospital FROM hospital WHERE id_hospital = ?");
		        psHospitalDestino.setInt(1, m_ID_Hospital_Destino);
		        rs = psHospitalDestino.executeQuery();

		        if (!rs.next()) {
		            throw new GestionDonacionesSangreException(
		                    GestionDonacionesSangreException.HOSPITAL_NO_EXISTE);
		        }

		        rs.close();
		        rs = null;

		        java.sql.Date fechaTraspasoSql =
		                new java.sql.Date(m_Fecha_Traspaso.getTime());

		        // Buscar todos los traspasos que coincidan
		        psTraspasos = con.prepareStatement(
		                "SELECT id_traspaso, cantidad " +
		                "FROM traspaso " +
		                "WHERE id_tipo_sangre = ? " +
		                "AND id_hospital_origen = ? " +
		                "AND id_hospital_destino = ? " +
		                "AND fecha_traspaso = ?");

		        psTraspasos.setInt(1, m_ID_Tipo_Sangre);
		        psTraspasos.setInt(2, m_ID_Hospital_Origen);
		        psTraspasos.setInt(3, m_ID_Hospital_Destino);
		        psTraspasos.setDate(4, fechaTraspasoSql);

		        rs = psTraspasos.executeQuery();

		        psUpdateDestino = con.prepareStatement(
		                "UPDATE reserva_hospital " +
		                "SET cantidad = cantidad - ? " +
		                "WHERE id_tipo_sangre = ? " +
		                "AND id_hospital = ?");

		        psUpdateOrigen = con.prepareStatement(
		                "UPDATE reserva_hospital " +
		                "SET cantidad = cantidad + ? " +
		                "WHERE id_tipo_sangre = ? " +
		                "AND id_hospital = ?");

		        boolean hayTraspasos = false;

		        while (rs.next()) {
		            hayTraspasos = true;

		            float cantidad = rs.getFloat("cantidad");

		            // La cantidad del traspaso no puede ser negativa
		            if (cantidad < 0) {
		                throw new GestionDonacionesSangreException(
		                        GestionDonacionesSangreException.VALOR_CANTIDAD_TRASPASO_INCORRECTO);
		            }

		            // Restar del hospital destino
		            psUpdateDestino.setFloat(1, cantidad);
		            psUpdateDestino.setInt(2, m_ID_Tipo_Sangre);
		            psUpdateDestino.setInt(3, m_ID_Hospital_Destino);

		            int filasDestino = psUpdateDestino.executeUpdate();

		            if (filasDestino == 0) {
		                throw new SQLException(
		                        "No existe reserva_hospital para el hospital destino y tipo de sangre");
		            }

		            // Sumar al hospital origen
		            psUpdateOrigen.setFloat(1, cantidad);
		            psUpdateOrigen.setInt(2, m_ID_Tipo_Sangre);
		            psUpdateOrigen.setInt(3, m_ID_Hospital_Origen);

		            int filasOrigen = psUpdateOrigen.executeUpdate();

		            if (filasOrigen == 0) {
		                throw new SQLException(
		                        "No existe reserva_hospital para el hospital origen y tipo de sangre");
		            }
		        }

		        rs.close();
		        rs = null;

		        // Borrar los traspasos anulados
		        if (hayTraspasos) {
		            psDeleteTraspasos = con.prepareStatement(
		                    "DELETE FROM traspaso " +
		                    "WHERE id_tipo_sangre = ? " +
		                    "AND id_hospital_origen = ? " +
		                    "AND id_hospital_destino = ? " +
		                    "AND fecha_traspaso = ?");

		            psDeleteTraspasos.setInt(1, m_ID_Tipo_Sangre);
		            psDeleteTraspasos.setInt(2, m_ID_Hospital_Origen);
		            psDeleteTraspasos.setInt(3, m_ID_Hospital_Destino);
		            psDeleteTraspasos.setDate(4, fechaTraspasoSql);

		            psDeleteTraspasos.executeUpdate();
		        }

		        con.commit();
			
		} catch (SQLException e) {
		     if (con != null) {
		            con.rollback();
		        }

		        if (e instanceof GestionDonacionesSangreException) {
		            throw e;
		        }		
			
			logger.error(e.getMessage());
			throw e;		

		} finally {
			 if (rs != null) rs.close();
		        if (psTipoSangre != null) psTipoSangre.close();
		        if (psHospitalOrigen != null) psHospitalOrigen.close();
		        if (psHospitalDestino != null) psHospitalDestino.close();
		        if (psTraspasos != null) psTraspasos.close();
		        if (psUpdateDestino != null) psUpdateDestino.close();
		        if (psUpdateOrigen != null) psUpdateOrigen.close();
		        if (psDeleteTraspasos != null) psDeleteTraspasos.close();
		        if (con != null) con.close();
		}		
	}
	
	public static void consulta_traspasos(String m_Tipo_Sangre)
			throws SQLException {

				
		PoolDeConexiones pool = PoolDeConexiones.getInstance();
		Connection con=null;
PreparedStatement pst_check = null;
		PreparedStatement pst_query = null;
		ResultSet rs = null;

	
		try{
			con = pool.getConnection();


			//Verificar que el tipo de sangre existe en tipo_sangre.
			pst_check = con.prepareStatement(
					"SELECT id_tipo_sangre FROM tipo_sangre WHERE UPPER(descripcion) = UPPER(?)");
			pst_check.setString(1, m_Tipo_Sangre);
			rs = pst_check.executeQuery();

			if (!rs.next()) {
				throw new GestionDonacionesSangreException(
						GestionDonacionesSangreException.TIPO_SANGRE_NO_EXISTE);
			}
			rs.close();
			rs = null;

			
			// Consulta principal: traspaso + tipo_sangre + hospital (x2)
			//    + reserva_hospital (x2), ordenada por id_hospital_destino
			//    y fecha_traspaso 
			pst_query = con.prepareStatement(
					"SELECT "
					+ "  t.id_traspaso, "
					+ "  ts.descripcion     AS tipo_sangre, "
					+ "  h_orig.nombre      AS hosp_origen, "
					+ "  h_orig.localidad   AS loc_origen, "
					+ "  h_dest.nombre      AS hosp_destino, "
					+ "  h_dest.localidad   AS loc_destino, "
					+ "  t.cantidad, "
					+ "  t.fecha_traspaso, "
					+ "  rh_orig.cantidad   AS reserva_origen, "
					+ "  rh_dest.cantidad   AS reserva_destino "
					+ "FROM traspaso t "
					+ "  JOIN tipo_sangre ts  ON t.id_tipo_sangre      = ts.id_tipo_sangre "
					+ "  JOIN hospital h_orig ON t.id_hospital_origen  = h_orig.id_hospital "
					+ "  JOIN hospital h_dest ON t.id_hospital_destino = h_dest.id_hospital "
					+ "  LEFT JOIN reserva_hospital rh_orig "
					+ "       ON t.id_tipo_sangre = rh_orig.id_tipo_sangre "
					+ "       AND t.id_hospital_origen  = rh_orig.id_hospital "
					+ "  LEFT JOIN reserva_hospital rh_dest "
					+ "       ON t.id_tipo_sangre = rh_dest.id_tipo_sangre "
					+ "       AND t.id_hospital_destino = rh_dest.id_hospital "
					+ "WHERE UPPER(ts.descripcion) = UPPER(?) "
					+ "ORDER BY t.id_hospital_destino, t.fecha_traspaso");

			pst_query.setString(1, m_Tipo_Sangre);
			rs = pst_query.executeQuery();

			
			// 3. Mostrar resultados por consola
			System.out.println("\n--- Traspasos de tipo: " + m_Tipo_Sangre + " ---");
			System.out.printf("%-5s %-10s %-28s %-28s %-8s %-12s %-13s %-13s%n",
					"ID", "Tipo", "Hospital origen", "Hospital destino",
					"Cantidad", "Fecha", "Reserva orig.", "Reserva dest.");
			System.out.println("---------------------------------------------------------------------------------------------------------");

			int total = 0;
			while (rs.next()) {
				System.out.printf("%-5d %-10s %-28s %-28s %-8.2f %-12s %-13.2f %-13.2f%n",
						rs.getInt("id_traspaso"),
						rs.getString("tipo_sangre"),
						rs.getString("hosp_origen") + " (" + rs.getString("loc_origen") + ")",
						rs.getString("hosp_destino") + " (" + rs.getString("loc_destino") + ")",
						rs.getFloat("cantidad"),
						rs.getDate("fecha_traspaso").toString(),
						rs.getFloat("reserva_origen"),
						rs.getFloat("reserva_destino"));
				total++;
			}

			if (total == 0) {
				System.out.println("  (No hay traspasos para este tipo de sangre)");
			}
			System.out.println("Total: " + total + " traspaso(s)\n");

			// Exito -> commit
			con.commit();
			
		} catch (SQLException e) {
            // En cualquier excepcion se deshacen los cambios de la transaccion
            if (con != null) {
            con.rollback();
            }

           // Las excepciones propias de la practica solo se relanzan
           // al metodo que haya llamado a la transaccion.
           if (e instanceof GestionDonacionesSangreException) {
              throw e;
            }

    // Las SQLException normales se registran en el logger
    // y tambien se relanzan.
    logger.error(e.getMessage());
    throw e;		

		} finally {
			if (rs        != null) try { rs.close();        } catch (SQLException ex) { logger.error(ex.getMessage()); }
            if (pst_query != null) try { pst_query.close(); } catch (SQLException ex) { logger.error(ex.getMessage()); }
            if (pst_check != null) try { pst_check.close(); } catch (SQLException ex) { logger.error(ex.getMessage()); }
            if (con       != null) try { con.close();       } catch (SQLException ex) { logger.error(ex.getMessage()); }
		}		
	}
	
	static public void creaTablas() {
		ExecuteScript.run(script_path + "gestion_donaciones_sangre.sql");
	}
	
	static void reiniciaDatos() throws SQLException {
		PoolDeConexiones pool = PoolDeConexiones.getInstance();

		CallableStatement cll_reinicia = null;
		Connection conn = null;

		try {
			conn = pool.getConnection();
			cll_reinicia = conn.prepareCall("{call inicializa_test}");
			cll_reinicia.execute();
		} finally {
			if (cll_reinicia != null) cll_reinicia.close();
			if (conn != null) conn.close();
		}
	}

	static void tests() throws SQLException {
		creaTablas();

		// Caso 1: correcto
		try {
			reiniciaDatos();
			realizar_donacion("12345678A", 1, 0.30f, java.sql.Date.valueOf("2025-02-20"));
			System.out.println("OK caso correcto");
		} catch (SQLException e) {
			System.out.println("FALLO caso correcto: " + e.getMessage());
		}

		// Caso 2: donante inexistente
		try {
			reiniciaDatos();
			realizar_donacion("00000000Z", 1, 0.30f, java.sql.Date.valueOf("2025-02-20"));
			System.out.println("FALLO donante inexistente: no lanzó excepción");
		} catch (GestionDonacionesSangreException e) {
			if (e.getErrorCode() == GestionDonacionesSangreException.DONANTE_NO_EXISTE) {
				System.out.println("OK donante inexistente");
			} else {
				System.out.println("FALLO donante inexistente: código " + e.getErrorCode());
			}
		}

		// Caso 3: hospital inexistente
		try {
			reiniciaDatos();
			realizar_donacion("12345678A", 99, 0.30f, java.sql.Date.valueOf("2025-02-20"));
			System.out.println("FALLO hospital inexistente: no lanzó excepción");
		} catch (GestionDonacionesSangreException e) {
			if (e.getErrorCode() == GestionDonacionesSangreException.HOSPITAL_NO_EXISTE) {
				System.out.println("OK hospital inexistente");
			} else {
				System.out.println("FALLO hospital inexistente: código " + e.getErrorCode());
			}
		}

		// Caso 4: cantidad incorrecta
		try {
			reiniciaDatos();
			realizar_donacion("12345678A", 1, 0.60f, java.sql.Date.valueOf("2025-02-20"));
			System.out.println("FALLO cantidad incorrecta: no lanzó excepción");
		} catch (GestionDonacionesSangreException e) {
			if (e.getErrorCode() == GestionDonacionesSangreException.VALOR_CANTIDAD_DONACION_INCORRECTO) {
				System.out.println("OK cantidad incorrecta");
			} else {
				System.out.println("FALLO cantidad incorrecta: código " + e.getErrorCode());
			}
		}

		// Caso 5: donante excede 15 días
		try {
			reiniciaDatos();
			realizar_donacion("12345678A", 1, 0.30f, java.sql.Date.valueOf("2025-01-20"));
			System.out.println("FALLO donante excede: no lanzó excepción");
		} catch (GestionDonacionesSangreException e) {
			if (e.getErrorCode() == GestionDonacionesSangreException.DONANTE_EXCEDE) {
				System.out.println("OK donante excede");
			} else {
				System.out.println("FALLO donante excede: código " + e.getErrorCode());
			}
		}


		// TESTS DE consulta_traspasos

		// Caso 6: Tipo existente CON traspasos -> muestra filas
		try {
			reiniciaDatos();
			consulta_traspasos("Tipo A.");
			System.out.println("OK consulta Tipo A");
		} catch (SQLException e) {
			System.out.println("FALLO consulta Tipo A: " + e.getMessage());
		}

	
		// Caso 7: Tipo existente CON traspasos -> 'Tipo B.'
		try {
			reiniciaDatos();
			consulta_traspasos("Tipo B.");
			System.out.println("OK consulta Tipo B");
		} catch (SQLException e) {
			System.out.println("TEST 2 ERROR inesperado: " + e.getMessage());
		}

		
		// Caso 8: Tipo existente SIN traspasos -> lista vacia, sin excepcion
		// 'Tipo O.' existe en tipo_sangre pero no tiene traspasos en los datos
		try {
			reiniciaDatos();
			consulta_traspasos("Tipo O.");
			System.out.println("TEST 3 OK: lista vacia, sin excepcion");
		} catch (GestionDonacionesSangreException e) {
			System.out.println("TEST 3 ERROR: no debia lanzar excepcion, codigo=" + e.getErrorCode());
		} catch (SQLException e) {
			System.out.println("TEST 3 ERROR inesperado: " + e.getMessage());
		}

		// Caso 9: Tipo NO existe -> GestionDonacionesSangreException codigo 2
		try {
			reiniciaDatos();
			consulta_traspasos("Tipo X.");
			System.out.println("TEST 4 ERROR: debia lanzar GestionDonacionesSangreException codigo 2");
		} catch (GestionDonacionesSangreException e) {
			if (e.getErrorCode() == GestionDonacionesSangreException.TIPO_SANGRE_NO_EXISTE) {
				System.out.println("TEST 4 OK: excepcion correcta. Codigo="
						+ e.getErrorCode() + " Mensaje=" + e.getMessage());
			} else {
				System.out.println("TEST 4 ERROR: codigo incorrecto=" + e.getErrorCode());
			}
		}

		
		// Caso 10: Verificacion del orden (id_hospital_destino, fecha_traspaso)
		// 'Tipo A.' tiene: destino=2 el 11/01 y destino=3 el 16/01
		try {
			reiniciaDatos();
			System.out.println("Esperado: primero Aranda(id=2) fecha=11/01, luego Leon(id=3) fecha=16/01");
			consulta_traspasos("Tipo A.");
			System.out.println("TEST 5 OK: verifique visualmente el orden");
		} catch (SQLException e) {
			System.out.println("TEST 5 ERROR inesperado: " + e.getMessage());
		}
		// TESTS DE anular_traspaso

		// Caso 11: anular traspaso correcto
		try {
		    reiniciaDatos();

		    /*
		     * Según tus propios comentarios:
		     * Tipo A. tiene un traspaso destino=2 fecha=2025-01-11.
		     * Normalmente será:
		     * id_tipo_sangre = 1
		     * hospital origen = 1
		     * hospital destino = 2
		     */
		    anular_traspaso(1, 1, 2, java.sql.Date.valueOf("2025-01-11"));

		    System.out.println("OK anular traspaso correcto");

		    // Opcional: comprobar visualmente que ya no aparece ese traspaso
		    consulta_traspasos("Tipo A.");

		} catch (SQLException e) {
		    System.out.println("FALLO anular traspaso correcto: " + e.getMessage());
		}


		// Caso 12: tipo de sangre inexistente
		try {
		    reiniciaDatos();

		    anular_traspaso(99, 1, 2, java.sql.Date.valueOf("2025-01-11"));

		    System.out.println("FALLO anular tipo inexistente: no lanzó excepción");

		} catch (GestionDonacionesSangreException e) {
		    if (e.getErrorCode() == GestionDonacionesSangreException.TIPO_SANGRE_NO_EXISTE) {
		        System.out.println("OK anular tipo inexistente");
		    } else {
		        System.out.println("FALLO anular tipo inexistente: código " + e.getErrorCode());
		    }
		}


		// Caso 13: hospital origen inexistente
		try {
		    reiniciaDatos();

		    anular_traspaso(1, 99, 2, java.sql.Date.valueOf("2025-01-11"));

		    System.out.println("FALLO anular hospital origen inexistente: no lanzó excepción");

		} catch (GestionDonacionesSangreException e) {
		    if (e.getErrorCode() == GestionDonacionesSangreException.HOSPITAL_NO_EXISTE) {
		        System.out.println("OK anular hospital origen inexistente");
		    } else {
		        System.out.println("FALLO anular hospital origen inexistente: código " + e.getErrorCode());
		    }
		}


		// Caso 14: hospital destino inexistente
		try {
		    reiniciaDatos();

		    anular_traspaso(1, 1, 99, java.sql.Date.valueOf("2025-01-11"));

		    System.out.println("FALLO anular hospital destino inexistente: no lanzó excepción");

		} catch (GestionDonacionesSangreException e) {
		    if (e.getErrorCode() == GestionDonacionesSangreException.HOSPITAL_NO_EXISTE) {
		        System.out.println("OK anular hospital destino inexistente");
		    } else {
		        System.out.println("FALLO anular hospital destino inexistente: código " + e.getErrorCode());
		    }
		}


		// Caso 15: no existe ningún traspaso con esos datos
		// No debe lanzar excepción. Simplemente no borra nada.
		try {
		    reiniciaDatos();

		    anular_traspaso(1, 1, 2, java.sql.Date.valueOf("2030-01-01"));

		    System.out.println("OK anular traspaso inexistente: no hay cambios ni excepción");

		} catch (SQLException e) {
		    System.out.println("FALLO anular traspaso inexistente: " + e.getMessage());
		}
	}
}