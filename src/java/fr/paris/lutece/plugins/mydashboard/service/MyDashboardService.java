/*
 * Copyright (c) 2002-2013, Mairie de Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.mydashboard.service;

import fr.paris.lutece.plugins.mydashboard.business.IMyDashboardConfigurationDAO;
import fr.paris.lutece.plugins.mydashboard.business.MyDashboardConfiguration;
import fr.paris.lutece.portal.service.plugin.Plugin;
import fr.paris.lutece.portal.service.spring.SpringContextService;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.web.LocalVariables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;


/**
 * Dashboard Service
 */
public final class MyDashboardService
{
    private static final String SESSION_LIST_DASHBOARD = "mydashboard.sessionListMyDashboard";
    private static final String SESSION_LIST_DASHBOARD_CONFIG = "mydashboard.sessionListMyDashboardConfig";

    private IMyDashboardConfigurationDAO _myDashboardComponentDAO = SpringContextService
            .getBean( "mydashboard.myDashboardConfigurationDAO" );
    private static MyDashboardService _singleton = new MyDashboardService( );

    /**
     * Private Constructor
     */
    private MyDashboardService( )
    {
    }

    /**
     * Return the unique instance
     * @return The instance
     */
    public static MyDashboardService getInstance( )
    {
        return _singleton;
    }

    /**
     * Get the list of MyDashboardComponent
     * @return The list of MyDashboard components
     */
    public List<IMyDashboardComponent> getMyDashboardComponentsList( )
    {
        return SpringContextService.getBeansOfType( IMyDashboardComponent.class );
    }

    /**
     * Get the list of MyDashboardConfiguration associated with a given user
     * @param strUserName The name of the user to get the configurations of
     * @return The list of MyDashboardConfiguration. Note that the returned list
     *         is saved in the session if this is a request context
     */
    public List<MyDashboardConfiguration> getUserConfig( String strUserName )
    {
        List<MyDashboardConfiguration> listDashboardConfigs = getMyDashboardConfigListFromSession( );
        if ( listDashboardConfigs != null )
        {
            return listDashboardConfigs;
        }
        listDashboardConfigs = _myDashboardComponentDAO.findByUserName( strUserName, MyDashboardPlugin.getPlugin( ) );
        if ( listDashboardConfigs.size( ) == 0 )
        {
            // If there is no dashboard configured, we generate the configuration
            List<IMyDashboardComponent> listDashboardComponents = getMyDashboardComponentsList( );
            Collections.sort( listDashboardComponents );
            int nOrder = 1;
            for ( IMyDashboardComponent dashboardComponent : listDashboardComponents )
            {
                MyDashboardConfiguration config = new MyDashboardConfiguration( );
                config.setMyDashboardComponentId( dashboardComponent.getComponentId( ) );
                config.setUserName( strUserName );
                config.setOrder( nOrder++ );
                config.setHideDashboard( false );
                listDashboardConfigs.add( config );
            }
        }
        else
        {
            Collections.sort( listDashboardConfigs );
        }
        saveMyDashboardConfigListInSession( listDashboardConfigs );
        return listDashboardConfigs;
    }

    /**
     * Get the list of dashboards components associated with a given user name.
     * The list is sorted with the order of each component in the configuration,
     * and contains only enabled and displayed components
     * @param strUserName The name of the user to get the dashboard components
     *            of
     * @return The list of dashboards components
     */
    public List<IMyDashboardComponent> getDashboardComponentListFromUserName( String strUserName )
    {
        List<IMyDashboardComponent> listComponents = getMyDashboardListFromSession( );
        if ( listComponents != null )
        {
            return listComponents;
        }

        List<IMyDashboardComponent> listComponentsSorted;
        List<MyDashboardConfiguration> listUserConfig = getUserConfig( strUserName );

        if ( listUserConfig != null )
        {
            listComponents = getMyDashboardComponentsList( );
            listComponentsSorted = new ArrayList<IMyDashboardComponent>( listComponents.size( ) );

            for ( MyDashboardConfiguration config : listUserConfig )
            {
                if ( !config.getHideDashboard( ) )
                {
                    for ( IMyDashboardComponent component : listComponents )
                    {
                        if ( StringUtils.equals( config.getMyDashboardComponentId( ), component.getComponentId( ) ) )
                        {
                            listComponentsSorted.add( component );
                            listComponents.remove( component );
                            break;
                        }
                    }
                }
            }
            if ( listComponents.size( ) > 0 )
            {
                AppLogService.error( "MyDashboard : dashboard(s) found without user configuration" );
                Collections.sort( listComponents );
                listComponentsSorted.addAll( listComponents );
            }
        }
        else
        {
            listComponentsSorted = getMyDashboardComponentsList( );
        }
        saveMyDashboardListInSession( listComponentsSorted );
        return listComponentsSorted;
    }

    /**
     * Delete a user configuration from its user name, and reset the list of
     * configurations saved in session if any
     * @param strUserName The name of the user to remove the configuration of
     */
    public void deleteConfigByUserName( String strUserName )
    {
        _myDashboardComponentDAO.removeByUserName( strUserName, MyDashboardPlugin.getPlugin( ) );
        saveMyDashboardConfigListInSession( null );
    }

    /**
     * Saves a list of configuration into the database. The configuration list
     * is also saved in the session
     * @param listMyDashboardsConfig The list of configuration to save
     */
    public void createsConfigList( List<MyDashboardConfiguration> listMyDashboardsConfig )
    {
        Plugin plugin = MyDashboardPlugin.getPlugin( );
        for ( MyDashboardConfiguration config : listMyDashboardsConfig )
        {
            _myDashboardComponentDAO.insertConfiguration( config, plugin );
        }
        saveMyDashboardConfigListInSession( listMyDashboardsConfig );
    }

    /**
     * Save a list of dashboards in session. If this is not a request context,
     * then do nothing
     * @param listMyDashboards The list of dashboards to save
     */
    private void saveMyDashboardListInSession( List<IMyDashboardComponent> listMyDashboards )
    {
        if ( LocalVariables.getRequest( ) != null )
        {
            HttpServletRequest request = LocalVariables.getRequest( );
            request.getSession( ).setAttribute( SESSION_LIST_DASHBOARD, listMyDashboards );
        }
    }

    /**
     * Get the list of dashboards from the session.
     * @return The list of dashboards from the session. If this is not a request
     *         context, or if there is no dashboard list saved in session,
     *         return null.
     */
    @SuppressWarnings( "unchecked" )
    private List<IMyDashboardComponent> getMyDashboardListFromSession( )
    {
        if ( LocalVariables.getRequest( ) != null )
        {
            HttpServletRequest request = LocalVariables.getRequest( );
            return (List<IMyDashboardComponent>) request.getSession( ).getAttribute( SESSION_LIST_DASHBOARD );
        }
        return null;
    }

    /**
     * Save a list of dashboard configurations in session. If this is not a
     * request context, then do nothing
     * @param listMyDashboardsConfig The list of dashboards to save
     */
    private void saveMyDashboardConfigListInSession( List<MyDashboardConfiguration> listMyDashboardsConfig )
    {
        if ( LocalVariables.getRequest( ) != null )
        {
            HttpServletRequest request = LocalVariables.getRequest( );
            request.getSession( ).setAttribute( SESSION_LIST_DASHBOARD_CONFIG, listMyDashboardsConfig );
        }
    }

    /**
     * Get the list of dashboard configurations from the session.
     * @return The list of dashboard configurations from the session. If this is
     *         not a request context, or if there is no dashboard configuration
     *         list saved in session, return null.
     */
    @SuppressWarnings( "unchecked" )
    private List<MyDashboardConfiguration> getMyDashboardConfigListFromSession( )
    {
        if ( LocalVariables.getRequest( ) != null )
        {
            HttpServletRequest request = LocalVariables.getRequest( );
            return (List<MyDashboardConfiguration>) request.getSession( ).getAttribute( SESSION_LIST_DASHBOARD_CONFIG );
        }
        return null;
    }
}
